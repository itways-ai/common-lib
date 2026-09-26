package com.itways.security.servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.itways.common.constants.ErrorCodes;
import com.itways.common.response.ApiResponse;
import com.itways.security.SecurityUtils;
import com.itways.security.SessionRevocationStore;
import com.itways.security.jwt.JwtTokenProvider;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	static final String TOKEN_REVOKED_MESSAGE = "Your session ended because the password was changed. Please sign in again.";

	private final JwtTokenProvider tokenProvider;
	private final SessionRevocationStore sessionRevocationStore;
	private final ObjectProvider<ObjectMapper> objectMapperProvider;

	private volatile ObjectMapper objectMapper;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String jwt = getJwtFromRequest(request);

		if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
			String tokenType = null;
			try {
				tokenType = tokenProvider.getTokenType(jwt);
			} catch (Exception e) {
				logger.error("Failed to read the token type: " + e.getMessage());
			}

			// A refresh token carries the same tenant binding as an access token
			// and a much longer life, so without this check a leaked one is a
			// week-long API session. It is good for /refresh-token and nothing
			// else. A null type is a user access token; CHANNEL_WEBHOOK is a
			// webhook's own token, which services forward to each other on
			// purpose (see ForwardedAuthFeignConfig) and must still be accepted.
			if (JwtTokenProvider.TYPE_REFRESH.equals(tokenType)) {
				logger.warn("Refresh token presented as a bearer credential — rejecting request");
				reject(response, "Invalid token: wrong token type", ErrorCodes.UNAUTHORIZED);
				return;
			}

			String username = null;
			String accountId = null;
			try {
				username = tokenProvider.getUsernameFromToken(jwt);
				String accH = tokenProvider.getAccountIdHashedFromToken(jwt);
				String accE = tokenProvider.getAccountIdEncryptedFromToken(jwt);

				if (accH != null && accE != null) {
					String decryptedAcc = SecurityUtils.decrypt(accE);
					String hashedAcc = SecurityUtils.hash(decryptedAcc);
					if (hashedAcc.equals(accH)) {
						accountId = decryptedAcc;
					}
				}
			} catch (Exception e) {
				logger.error("Failed to decrypt or validate accountId: " + e.getMessage());
			}

			// A valid signature with a broken (or absent) tenant binding is a
			// forged or corrupted token. Reject it outright — the previous
			// behavior of proceeding as pseudo-tenant "N/A" let every
			// account-scoped query run against a shared bogus tenant.
			if (accountId == null) {
				logger.warn("JWT tenant binding failed (accH/accE mismatch or missing) — rejecting request");
				reject(response, "Invalid token: tenant binding failed", ErrorCodes.UNAUTHORIZED);
				return;
			}

			// A user's access tokens minted before their last password change
			// are dead (AS-03 C08), otherwise a stolen token outlives the change
			// by up to an hour. Only user ACCESS tokens: a CHANNEL_WEBHOOK token
			// is embedded in a provider's webhook URL and has nothing to do with
			// the user's password; refresh tokens were refused above.
			if (isAccessToken(tokenType) && sessionRevocationStore.isRevoked(accountId, issuedAt(jwt))) {
				logger.info("Access token predates the account's last password change — rejecting request");
				reject(response, TOKEN_REVOKED_MESSAGE, ErrorCodes.TOKEN_REVOKED);
				return;
			}

			// For simplicity in microservices, we might not have a full
			// UserDetailsService locally. We trust the JWT and the gateway, and
			// create a principal from the claims.
			UserDetails userDetails = new User(username, "", Collections.emptyList());

			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
					userDetails, null, userDetails.getAuthorities());

			// accountId for the resolver to pick up; tokenType so a service can
			// refuse a credential that is merely valid — a webhook token is
			// still the whole tenant, so anything destructive should look.
			authentication.setDetails(Map.of(
					"accountId", accountId,
					"tokenType", tokenType == null ? JwtTokenProvider.TYPE_ACCESS : tokenType,
					"remoteAddress", String.valueOf(request.getRemoteAddr())));

			SecurityContextHolder.getContext().setAuthentication(authentication);
		}

		filterChain.doFilter(request, response);
	}

	private static boolean isAccessToken(String tokenType) {
		return tokenType == null || JwtTokenProvider.TYPE_ACCESS.equals(tokenType);
	}

	/** {@code null} when unreadable — the store then treats the token as revoked if a change is on record. */
	private Instant issuedAt(String jwt) {
		try {
			return tokenProvider.getIssuedAt(jwt);
		} catch (Exception e) {
			logger.warn("Failed to read the token's issued-at: " + e.getClass().getSimpleName());
			return null;
		}
	}

	/**
	 * Ends the request with 401 in the platform's {@link ApiResponse} envelope,
	 * the same shape every controller error has, so clients parse one format.
	 */
	private void reject(HttpServletResponse response, String message, String errorCode) throws IOException {
		SecurityContextHolder.clearContext();
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(objectMapper().writeValueAsString(ApiResponse.error(message, errorCode)));
	}

	/**
	 * The service's own ObjectMapper (java-time support and the platform's UTC
	 * format); a plain one with java-time registered when there is no single
	 * candidate. Resolved lazily: filters are created before much else.
	 */
	private ObjectMapper objectMapper() {
		ObjectMapper mapper = objectMapper;
		if (mapper == null) {
			mapper = objectMapperProvider.getIfUnique(() -> new ObjectMapper().findAndRegisterModules()
					.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
			objectMapper = mapper;
		}
		return mapper;
	}

	private String getJwtFromRequest(HttpServletRequest request) {
		String bearerToken = request.getHeader("Authorization");
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7);
		}
		return null;
	}
}
