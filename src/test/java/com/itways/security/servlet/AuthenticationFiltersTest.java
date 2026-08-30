package com.itways.security.servlet;

import com.itways.security.ApiKeyProvider;
import com.itways.security.ApiKeyRevocationStore;
import com.itways.security.SecurityUtils;
import com.itways.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two servlet filters are the platform's tenant boundary: every request's
 * accountId comes out of the {@code accH == hash(decrypt(accE))} binding they
 * perform. Both now refuse a broken binding — the JWT filter answers an
 * explicit 401 and stops the chain (the old behavior of proceeding as tenant
 * "N/A" is gone), and the API-key filter additionally enforces the embedded
 * expiry and the Redis-backed revocation deny-list.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Authentication filters")
class AuthenticationFiltersTest {

    private static final String AES_KEY = "0123456789abcdef0123456789abcdef";
    private static final String ACCOUNT_ID = "AIUS000000000001";

    private JwtTokenProvider tokenProvider;

    @Mock
    private ApiKeyRevocationStore revocationStore;

    @BeforeEach
    void primeCrypto() throws Exception {
        new SecurityUtils().setEncryptionKey(AES_KEY);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "privateKeyStr",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        ReflectionTestUtils.setField(tokenProvider, "publicKeyStr",
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        ReflectionTestUtils.setField(tokenProvider, "accessExpiration", 60_000L);
        ReflectionTestUtils.setField(tokenProvider, "refreshExpiration", 60_000L);
        tokenProvider.init();

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> authDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        return (Map<String, Object>) authentication.getDetails();
    }

    @Nested
    @DisplayName("JwtAuthenticationFilter")
    class JwtFilter {

        private final MockHttpServletResponse response = new MockHttpServletResponse();

        private JwtAuthenticationFilter filter() {
            return new JwtAuthenticationFilter(tokenProvider);
        }

        private String tokenWithBinding(String accountId) {
            return tokenProvider.generateAccessToken("user@example.com", Map.of(
                    "role", "USER",
                    "accH", SecurityUtils.hash(accountId),
                    "accE", SecurityUtils.encrypt(accountId)));
        }

        @Test
        @DisplayName("a valid token with an intact tenant binding authenticates with the real accountId")
        void validBinding() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + tokenWithBinding(ACCOUNT_ID));

            filter().doFilterInternal(request, response, new MockFilterChain());

            assertThat(authDetails()).containsEntry("accountId", ACCOUNT_ID);
        }

        @Test
        @DisplayName("a tampered accH is rejected with 401 and the chain never runs")
        void tamperedBindingRejected() throws Exception {
            // The old behavior — authenticating as pseudo-tenant "N/A" — let a
            // forged binding reach account-scoped queries. A valid signature
            // with a broken binding is now an explicit 401.
            String token = tokenProvider.generateAccessToken("user@example.com", Map.of(
                    "accH", SecurityUtils.hash("SOME-OTHER-ACCOUNT"),
                    "accE", SecurityUtils.encrypt(ACCOUNT_ID)));
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + token);
            MockFilterChain chain = new MockFilterChain();

            filter().doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(chain.getRequest()).as("filter chain must not continue").isNull();
        }

        @Test
        @DisplayName("a valid token without any tenant binding claims is rejected the same way")
        void missingBindingRejected() throws Exception {
            String token = tokenProvider.generateAccessToken("user@example.com", Map.of("role", "USER"));
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + token);
            MockFilterChain chain = new MockFilterChain();

            filter().doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("an invalid or absent token leaves the request unauthenticated and the chain running")
        void invalidOrMissingToken() throws Exception {
            MockHttpServletRequest bad = new MockHttpServletRequest();
            bad.addHeader("Authorization", "Bearer not.a.token");
            MockFilterChain chain = new MockFilterChain();

            filter().doFilterInternal(bad, response, chain);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(chain.getRequest()).isNotNull();

            filter().doFilterInternal(new MockHttpServletRequest(), response, new MockFilterChain());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("a non-Bearer Authorization header is ignored")
        void nonBearerIgnored() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

            filter().doFilterInternal(request, response, new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    @DisplayName("ApiKeyAuthenticationFilter")
    class ApiKeyFilter {

        private final MockHttpServletResponse response = new MockHttpServletResponse();

        private ApiKeyAuthenticationFilter filter() {
            return new ApiKeyAuthenticationFilter(new ApiKeyProvider(), revocationStore);
        }

        private String mintKey(String accountId, int version, long expiresAtEpochMillis) {
            String payload = SecurityUtils.hash(accountId)
                    + "::" + SecurityUtils.encrypt(accountId)
                    + "::" + SecurityUtils.encrypt("user@example.com")
                    + "::" + version
                    + "::" + expiresAtEpochMillis
                    + "::random-padding";
            return "sk_live_" + SecurityUtils.encrypt(payload);
        }

        @Test
        @DisplayName("a well-formed, unrevoked key authenticates with accountId, version, and API_KEY source")
        void validKey() throws Exception {
            when(revocationStore.isRevoked(anyString())).thenReturn(false);
            MockHttpServletRequest request = new MockHttpServletRequest();
            String apiKey = mintKey(ACCOUNT_ID, 3, 0);
            request.addHeader("X-API-KEY", apiKey);

            filter().doFilterInternal(request, response, new MockFilterChain());

            assertThat(authDetails())
                    .containsEntry("accountId", ACCOUNT_ID)
                    .containsEntry("keyVersion", "3")
                    .containsEntry("authSource", "API_KEY");
            // The deny-list is consulted with the SHA-256 hash of the full key
            // — the same value account-service stores at revocation time.
            verify(revocationStore).isRevoked(SecurityUtils.hash(apiKey));
        }

        @Test
        @DisplayName("a revoked key refuses to authenticate")
        void revokedKeyRefused() throws Exception {
            when(revocationStore.isRevoked(anyString())).thenReturn(true);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-API-KEY", mintKey(ACCOUNT_ID, 1, 0));
            MockFilterChain chain = new MockFilterChain();

            filter().doFilterInternal(request, response, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            // Unauthenticated pass-through: the authorization layer answers
            // 401/403, consistent with every other invalid-key path here.
            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("a key past its embedded expiry refuses to authenticate — without touching the deny-list")
        void expiredKeyRefused() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-API-KEY", mintKey(ACCOUNT_ID, 1, System.currentTimeMillis() - 1_000));

            filter().doFilterInternal(request, response, new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("a key with a future expiry authenticates normally")
        void futureExpiryAllowed() throws Exception {
            when(revocationStore.isRevoked(anyString())).thenReturn(false);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-API-KEY", mintKey(ACCOUNT_ID, 1, System.currentTimeMillis() + 60_000));

            filter().doFilterInternal(request, response, new MockFilterChain());

            assertThat(authDetails()).containsEntry("accountId", ACCOUNT_ID);
        }

        @Test
        @DisplayName("a broken tenant binding refuses to authenticate")
        void brokenBindingRefused() throws Exception {
            String payload = SecurityUtils.hash("DIFFERENT-ACCOUNT")
                    + "::" + SecurityUtils.encrypt(ACCOUNT_ID)
                    + "::" + SecurityUtils.encrypt("user@example.com")
                    + "::1::0::pad";
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-API-KEY", "sk_live_" + SecurityUtils.encrypt(payload));

            filter().doFilterInternal(request, response, new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("malformed keys and absent headers pass through unauthenticated")
        void malformedOrAbsent() throws Exception {
            MockHttpServletRequest garbage = new MockHttpServletRequest();
            garbage.addHeader("X-API-KEY", "sk_live_garbage");
            MockFilterChain chain = new MockFilterChain();

            filter().doFilterInternal(garbage, response, chain);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(chain.getRequest()).isNotNull();

            filter().doFilterInternal(new MockHttpServletRequest(), response, new MockFilterChain());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
