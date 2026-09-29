package com.itways.security.servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.CredentialsExpiredException;
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
import com.itways.web.correlation.CurrentRequestId;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component("jwtAuthenticationFilter")
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	/**
	 * The answer to a revoked access token (sign-out, password change,
	 * deactivation): 401 {@code AUTH_401}, like any other missing credential, so
	 * the portal tries its refresh token and, the session being over, signs out.
	 */
	static final String SESSION_ENDED_MESSAGE = "Your session has ended. Please sign in again.";

	private final JwtTokenProvider tokenProvider;
	private final SessionRevocationStore sessionRevocationStore;
	private final ObjectProvider<ObjectMapper> objectMapperProvider;

	private volatile ObjectMapper objectMapper;
	private volatile ApiResponseAuthenticationEntryPoint sessionEndedEntryPoint;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String jwt = getJwtFromRequest(request);
		// Verified once: every claim below comes from this (an RSA check per read otherwise).
		Claims claims = StringUtils.hasText(jwt) ? verifiedClaims(jwt) : null;

		if (claims != null) {
			String tokenType = claim(claims, JwtTokenProvider.CLAIM_TYPE);

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
				username = claims.getSubject();
				String accH = claim(claims, "accH");
				String accE = claim(claims, "accE");

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

			// A revoked user access token is dead before it expires (AS-03 C08,
			// PLT-05): its session was signed out (sid), or it predates the
			// account's cut-off (password change, deactivation). One Redis read;
			// Redis down means "not revoked" (see SessionRevocationStore). Only
			// user ACCESS tokens: a CHANNEL_WEBHOOK token is embedded in a
			// provider's webhook URL and belongs to no sign-in session; refresh
			// tokens were refused above. A token without sid (minted before
			// PLT-05) is judged by the cut-off only and dies at its expiry.
			if (isAccessToken(tokenType) && sessionRevocationStore.isRevoked(accountId, issuedAt(claims),
					claim(claims, JwtTokenProvider.CLAIM_SESSION_ID))) {
				logger.info("Access token was revoked (session ended or account cut-off) — rejecting request");
				SecurityContextHolder.clearContext();
				sessionEndedEntryPoint().commence(request, response,
						new CredentialsExpiredException("Access token revoked"));
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
					Sessions.DETAIL_ACCOUNT_ID, accountId,
					Sessions.DETAIL_TOKEN_TYPE, tokenType == null ? JwtTokenProvider.TYPE_ACCESS : tokenType,
					Sessions.DETAIL_REMOTE_ADDRESS, String.valueOf(request.getRemoteAddr())));

			SecurityContextHolder.getContext().setAuthentication(authentication);
		}

		filterChain.doFilter(request, response);
	}

	private static boolean isAccessToken(String tokenType) {
		return tokenType == null || JwtTokenProvider.TYPE_ACCESS.equals(tokenType);
	}

	/**
	 * The verified claims, or {@code null} for a token that fails the signature,
	 * expiry or key rules: the request then continues unauthenticated, and the
	 * service's entry point answers if the route needs a credential.
	 */
	private Claims verifiedClaims(String jwt) {
		try {
			return tokenProvider.getVerifiedClaims(jwt);
		} catch (Exception e) {
			// Expired or foreign tokens are routine: no stack trace per request.
			if (logger.isDebugEnabled()) {
				logger.debug("JWT rejected: " + e.getClass().getSimpleName());
			}
			return null;
		}
	}

	/** A string claim, or {@code null} when absent or not a string. */
	private String claim(Claims claims, String name) {
		try {
			return claims.get(name, String.class);
		} catch (Exception e) {
			logger.warn("Unreadable '" + name + "' claim: " + e.getClass().getSimpleName());
			return null;
		}
	}

	/** {@code null} when absent — the store then treats the token as revoked if a cut-off is on record. */
	private static Instant issuedAt(Claims claims) {
		return claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null;
	}

	/**
	 * The shared 401 answer ({@link ApiResponseAuthenticationEntryPoint},
	 * {@code AUTH_401} in the envelope) with the session-ended message. Called
	 * directly rather than left to the chain, so every service answers 401 even
	 * where its chain names no entry point (speech-service would answer an
	 * empty 403, which the portal does not treat as an ended session).
	 */
	private ApiResponseAuthenticationEntryPoint sessionEndedEntryPoint() {
		ApiResponseAuthenticationEntryPoint entryPoint = sessionEndedEntryPoint;
		if (entryPoint == null) {
			entryPoint = new ApiResponseAuthenticationEntryPoint(objectMapper(), SESSION_ENDED_MESSAGE);
			sessionEndedEntryPoint = entryPoint;
		}
		return entryPoint;
	}

	/**
	 * Ends the request with 401 in the platform's {@link ApiResponse} envelope,
	 * the same shape every controller error has, so clients parse one format
	 * (with the request id as {@code reference} when there is one, ARC-25).
	 */
	private void reject(HttpServletResponse response, String message, String errorCode) throws IOException {
		SecurityContextHolder.clearContext();
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter()
				.write(objectMapper().writeValueAsString(CurrentRequestId.stamp(ApiResponse.error(message, errorCode))));
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
