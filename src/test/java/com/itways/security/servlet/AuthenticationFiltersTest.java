package com.itways.security.servlet;

import com.itways.security.ApiKeyProvider;
import com.itways.security.SecurityUtils;
import com.itways.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

/**
 * The two servlet filters are the platform's tenant boundary: every request's
 * accountId comes out of the {@code accH == hash(decrypt(accE))} binding they
 * perform. The deliberate contrast pinned here: the API-key filter refuses to
 * authenticate on a failed binding, while the JWT filter authenticates anyway
 * with the literal tenant "N/A" — a defect worth keeping visible.
 */
@DisplayName("Authentication filters")
class AuthenticationFiltersTest {

    private static final String AES_KEY = "0123456789abcdef0123456789abcdef";
    private static final String ACCOUNT_ID = "AIUS000000000001";

    private JwtTokenProvider tokenProvider;

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
        @DisplayName("a tampered accH still authenticates — as tenant \"N/A\"")
        void tamperedBindingAuthenticatesAsNA() throws Exception {
            // NOTE: possible defect — a failed hash-vs-decrypt binding should be
            // a 401. Instead the request proceeds authenticated with the literal
            // tenant "N/A", so every account-scoped query filters on 'N/A' and a
            // shared pseudo-tenant becomes reachable. Pinned as current behavior.
            String token = tokenProvider.generateAccessToken("user@example.com", Map.of(
                    "accH", SecurityUtils.hash("SOME-OTHER-ACCOUNT"),
                    "accE", SecurityUtils.encrypt(ACCOUNT_ID)));
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + token);

            filter().doFilterInternal(request, response, new MockFilterChain());

            assertThat(authDetails()).containsEntry("accountId", "N/A");
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
            return new ApiKeyAuthenticationFilter(new ApiKeyProvider());
        }

        private String mintKey(String accountId, int version) {
            String payload = SecurityUtils.hash(accountId)
                    + "::" + SecurityUtils.encrypt(accountId)
                    + "::" + SecurityUtils.encrypt("user@example.com")
                    + "::" + version
                    + "::random-padding";
            return "sk_live_" + SecurityUtils.encrypt(payload);
        }

        @Test
        @DisplayName("a well-formed key authenticates with accountId, version, and API_KEY source")
        void validKey() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-API-KEY", mintKey(ACCOUNT_ID, 3));

            filter().doFilterInternal(request, response, new MockFilterChain());

            assertThat(authDetails())
                    .containsEntry("accountId", ACCOUNT_ID)
                    .containsEntry("keyVersion", "3")
                    .containsEntry("authSource", "API_KEY");
            // NOTE: possible defect (by omission) — the filter never consults
            // the api_keys table, so a REVOKED key authenticates identically.
            // Revocation currently has no runtime effect anywhere.
        }

        @Test
        @DisplayName("a broken tenant binding refuses to authenticate — unlike the JWT filter")
        void brokenBindingRefused() throws Exception {
            String payload = SecurityUtils.hash("DIFFERENT-ACCOUNT")
                    + "::" + SecurityUtils.encrypt(ACCOUNT_ID)
                    + "::" + SecurityUtils.encrypt("user@example.com")
                    + "::1::pad";
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
