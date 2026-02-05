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

import com.itways.security.JwtTokenProvider;
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
		try {
			String jwt = getJwtFromRequest(request);

			if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
				String username = tokenProvider.getUsernameFromToken(jwt);
				String accH = tokenProvider.getAccountIdHashedFromToken(jwt);
				String accE = tokenProvider.getAccountIdEncryptedFromToken(jwt);

				String accountId = null;
				if (accH != null && accE != null) {
					try {
						String decryptedAcc = SecurityUtils.decrypt(accE);
						String hashedAcc = SecurityUtils.hash(decryptedAcc);
						if (hashedAcc.equals(accH)) {
							accountId = decryptedAcc;
						}
					} catch (Exception e) {
						logger.error("Failed to decrypt or validate accountId: " + e.getMessage());
					}
				}

				// For simplicity in microservices, we might not have a full UserDetailsService
				// locally
				// We trust the JWT and the gateway. We can create a principal from the claims.
				UserDetails userDetails = new User(username, "", Collections.emptyList());

				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
						userDetails, null, userDetails.getAuthorities());

				// Set accountId in details for resolver to pick up
				authentication.setDetails(Map.of("accountId", accountId != null ? accountId : "N/A", "remoteAddress",
						request.getRemoteAddr()));

				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		} catch (Exception ex) {
			logger.error("Could not set user authentication in security context", ex);
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
