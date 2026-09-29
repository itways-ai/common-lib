package com.itways.security.servlet;

import com.itways.security.ApiKeyProvider;
import com.itways.security.ApiKeyStatusStore;
import com.itways.security.SecurityUtils;
import com.itways.security.core.ApiKeyCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Component("apiKeyAuthenticationFilter")
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyProvider apiKeyProvider;
    private final ApiKeyStatusStore apiKeyStatusStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-KEY");

        if (StringUtils.hasText(apiKey)) {
            try {
                // Decryption, account binding and embedded expiry: ApiKeyCodec's
                // rules, the same code the api-gateway runs at the edge.
                ApiKeyCodec.Result check = apiKeyProvider.check(apiKey);

                String accountId = null;
                if (check.status() == ApiKeyCodec.Status.INVALID) {
                    logger.error("API Key decryption or hash validation failed");
                } else if (check.status() == ApiKeyCodec.Status.EXPIRED) {
                    logger.warn("API Key is past its embedded expiry — refusing to authenticate");
                } else {
                    accountId = check.accountId();
                }

                // Allow-list, not deny-list (AS-08): a key works only while
                // account-service keeps it listed as active, so a revoked key
                // stays dead even if Redis loses its data. Unreachable store →
                // refused (fail closed).
                String keyHash = SecurityUtils.hash(apiKey);
                if (accountId != null && !apiKeyStatusStore.isActive(keyHash)) {
                    logger.warn("API Key is not on the active allow-list (revoked, unknown, or store unreachable)"
                            + " — refusing to authenticate");
                    accountId = null;
                }

                if (accountId != null) {
                    String username = check.payload().username();
                    int keyVersion = check.payload().keyVersion();

                    // Validated identity
                    UserDetails userDetails = new User(username, "", Collections.emptyList());
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

                    // Store accountId and keyVersion in details
                    authentication.setDetails(Map.of(
                            Sessions.DETAIL_ACCOUNT_ID, accountId,
                            Sessions.DETAIL_KEY_VERSION, String.valueOf(keyVersion),
                            Sessions.DETAIL_AUTH_SOURCE, Sessions.AUTH_SOURCE_API_KEY));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    apiKeyStatusStore.recordUse(keyHash);
                    logger.debug("Successfully authenticated via API Key for user: " + username);
                }
            } catch (Exception ex) {
                logger.error("API Key validation process failed", ex);
            }
        }

        filterChain.doFilter(request, response);
    }
}
