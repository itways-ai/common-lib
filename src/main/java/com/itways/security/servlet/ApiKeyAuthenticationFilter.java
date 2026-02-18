package com.itways.security.servlet;

import com.itways.security.ApiKeyProvider;
import com.itways.security.SecurityUtils;
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

@Component
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyProvider apiKeyProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-KEY");

        if (StringUtils.hasText(apiKey)) {
            try {
                String username = apiKeyProvider.getUsernameFromApiKey(apiKey);
                String accHash = apiKeyProvider.getAccountIdHashedFromApiKey(apiKey);
                String accEnc = apiKeyProvider.getAccountIdEncryptedFromApiKey(apiKey);
                int keyVersion = apiKeyProvider.getKeyVersion(apiKey);

                String accountId = null;
                try {
                    String decryptedAccount = SecurityUtils.decrypt(accEnc);
                    String hashedAccount = SecurityUtils.hash(decryptedAccount);

                    if (hashedAccount.equals(accHash)) {
                        accountId = decryptedAccount;
                    }
                } catch (Exception ex) {
                    logger.error("API Key decryption or hash validation failed: " + ex.getMessage());
                }

                if (accountId != null) {
                    // Validated identity
                    UserDetails userDetails = new User(username, "", Collections.emptyList());
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

                    // Store accountId and keyVersion in details
                    authentication.setDetails(Map.of(
                            "accountId", accountId,
                            "keyVersion", String.valueOf(keyVersion),
                            "authSource", "API_KEY"));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.debug("Successfully authenticated via API Key for user: " + username);
                }
            } catch (Exception ex) {
                logger.error("API Key validation process failed", ex);
            }
        }

        filterChain.doFilter(request, response);
    }
}
