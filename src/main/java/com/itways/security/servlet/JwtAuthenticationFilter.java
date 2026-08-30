package com.itways.security.servlet;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.itways.security.jwt.JwtTokenProvider;
import com.itways.security.SecurityUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtTokenProvider tokenProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String jwt = getJwtFromRequest(request);

		if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
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
				SecurityContextHolder.clearContext();
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.setContentType("application/json");
				response.getWriter().write("{\"success\":false,\"message\":\"Invalid token: tenant binding failed\"}");
				return;
			}

			// For simplicity in microservices, we might not have a full
			// UserDetailsService locally. We trust the JWT and the gateway, and
			// create a principal from the claims.
			UserDetails userDetails = new User(username, "", Collections.emptyList());

			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
					userDetails, null, userDetails.getAuthorities());

			// Set accountId in details for resolver to pick up
			authentication.setDetails(Map.of("accountId", accountId, "remoteAddress", request.getRemoteAddr()));

			SecurityContextHolder.getContext().setAuthentication(authentication);
		}

		filterChain.doFilter(request, response);
	}

	private String getJwtFromRequest(HttpServletRequest request) {
		String bearerToken = request.getHeader("Authorization");
		if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7);
		}
		return null;
	}
}
