package com.itways.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.itways.common.exception.InvalidApiKeyException;
import com.itways.security.core.ApiKeyCodec;
import com.itways.security.core.CredentialCrypto;
import com.itways.security.core.TokenVerifier;
import com.itways.security.jwt.JwtTokenProvider;
import com.itways.security.servlet.ApiKeyAuthenticationFilter;

/**
 * common-lib's Spring classes run the security core's rules: same crypto,
 * same API-key slots, same token verdicts, and the servlet API-key filter
 * still authenticates exactly as before.
 */
class SecurityCoreDelegationTest {

    private static final String SECRET = "delegation-test-secret";
    private static final String ACCOUNT_ID = "4242";

    @BeforeEach
    void configureKey() {
        new SecurityUtils().setEncryptionKey(SECRET);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void securityUtilsIsTheCoreCryptoWithTheConfiguredKey() throws Exception {
        SecretKeySpec key = CredentialCrypto.deriveKey(SECRET);

        assertThat(SecurityUtils.encryptionKey()).isEqualTo(key);
        assertThat(CredentialCrypto.decrypt(key, SecurityUtils.encrypt("4242"))).isEqualTo("4242");
        assertThat(SecurityUtils.decrypt(CredentialCrypto.encrypt(key, "4242"))).isEqualTo("4242");
        assertThat(SecurityUtils.hash("4242")).isEqualTo(CredentialCrypto.hash("4242"));
        assertThatThrownBy(() -> SecurityUtils.decrypt("bm90LWNpcGhlcnRleHQ=")).isInstanceOf(RuntimeException.class)
                .hasMessage("Error decrypting value");
    }

    @Nested
    class ApiKeys {

        private final ApiKeyProvider provider = new ApiKeyProvider();

        @Test
        void theProviderReadsTheCodecSlots() {
            String key = apiKey("7", 1_900_000_000_000L);

            assertThat(provider.getAccountIdHashedFromApiKey(key)).isEqualTo(SecurityUtils.hash(ACCOUNT_ID));
            assertThat(SecurityUtils.decrypt(provider.getAccountIdEncryptedFromApiKey(key))).isEqualTo(ACCOUNT_ID);
            assertThat(SecurityUtils.decrypt(provider.getUsernameFromApiKey(key))).isEqualTo("user@example.test");
            assertThat(provider.getKeyVersion(key)).isEqualTo(7);
            assertThat(provider.getExpiresAtEpochMillis(key)).isEqualTo(1_900_000_000_000L);
        }

        @Test
        void aBadKeyThrowsInvalidApiKeyAndItsNumbersDegradeToZero() {
            assertThatThrownBy(() -> provider.getUsernameFromApiKey("pk_live_x"))
                    .isInstanceOf(InvalidApiKeyException.class).hasMessage("Invalid API Key format");
            assertThatThrownBy(() -> provider.getUsernameFromApiKey("sk_live_x"))
                    .isInstanceOf(InvalidApiKeyException.class).hasMessage("Invalid API Key");
            assertThat(provider.getKeyVersion("sk_live_x")).isZero();
            assertThat(provider.getExpiresAtEpochMillis("sk_live_x")).isZero();
        }

        @Test
        void theFilterAuthenticatesAValidActiveKey() throws Exception {
            String key = apiKey("2", 0L);
            ApiKeyStatusStore store = mock(ApiKeyStatusStore.class);
            when(store.isActive(SecurityUtils.hash(key))).thenReturn(true);

            MockFilterChain chain = filter(store, key);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(authentication.getName()).isEqualTo(provider.getUsernameFromApiKey(key));
            assertThat(authentication.getDetails())
                    .isEqualTo(Map.of("accountId", ACCOUNT_ID, "keyVersion", "2", "authSource", "API_KEY"));
            verify(store).recordUse(SecurityUtils.hash(key));
            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        void theFilterRefusesInactiveExpiredAndInvalidKeysButContinuesTheChain() throws Exception {
            ApiKeyStatusStore inactive = mock(ApiKeyStatusStore.class);
            ApiKeyStatusStore active = mock(ApiKeyStatusStore.class);
            when(active.isActive(anyString())).thenReturn(true);

            for (Object[] c : new Object[][] { { inactive, apiKey("1", 0L) },
                    { active, apiKey("1", System.currentTimeMillis() - 1000) },
                    { active, "sk_live_x" }, { active, "garbage" } }) {
                MockFilterChain chain = filter((ApiKeyStatusStore) c[0], (String) c[1]);

                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
                assertThat(chain.getRequest()).isNotNull();
            }
            verify(active, never()).recordUse(anyString());
        }

        @Test
        void theProviderCheckIsTheCodecCheck() {
            ApiKeyCodec.Result result = provider.check(apiKey("1", 0L));

            assertThat(result.status()).isEqualTo(ApiKeyCodec.Status.VALID);
            assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        }

        private MockFilterChain filter(ApiKeyStatusStore store, String key) throws Exception {
            SecurityContextHolder.clearContext();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
            request.addHeader("X-API-KEY", key);
            MockFilterChain chain = new MockFilterChain();
            new ApiKeyAuthenticationFilter(provider, store).doFilter(request, new MockHttpServletResponse(), chain);
            return chain;
        }

        private String apiKey(String version, long expiresAt) {
            String base = String.join("::", SecurityUtils.hash(ACCOUNT_ID), SecurityUtils.encrypt(ACCOUNT_ID),
                    SecurityUtils.encrypt("user@example.test"), version, String.valueOf(expiresAt));
            return "sk_live_" + SecurityUtils.encrypt(base + "::" + "p".repeat(200));
        }
    }

    @Nested
    class Tokens {

        @Test
        void jwtTokenProviderVerifiesAsTheCoreDoes() throws Exception {
            KeyPair platform = rsa();
            JwtTokenProvider provider = new JwtTokenProvider();
            ReflectionTestUtils.setField(provider, "privateKeyStr",
                    Base64.getEncoder().encodeToString(platform.getPrivate().getEncoded()));
            ReflectionTestUtils.setField(provider, "publicKeyStr",
                    Base64.getEncoder().encodeToString(platform.getPublic().getEncoded()));
            ReflectionTestUtils.setField(provider, "accessExpiration", 60_000L);
            ReflectionTestUtils.setField(provider, "refreshExpiration", 60_000L);
            provider.init();
            TokenVerifier core = new TokenVerifier(platform.getPublic(), null, null, null);

            String access = provider.generateAccessToken("u", Map.of("accH", "h"));
            String refresh = provider.generateRefreshToken("u");

            assertThat(provider.getVerifiedClaims(access).getSubject()).isEqualTo(core.verify(access).getSubject());
            assertThat(provider.getAccountIdHashedFromToken(access)).isEqualTo(core.verify(access).get("accH"));
            assertThat(core.check(access).kind()).isEqualTo(TokenVerifier.Kind.ACCESS);
            assertThat(provider.getTokenType(refresh)).isEqualTo(JwtTokenProvider.TYPE_REFRESH);
            assertThat(core.check(refresh).kind()).isEqualTo(TokenVerifier.Kind.REFRESH);
            assertThat(provider.validateToken("a.b.c")).isFalse();
        }

        private KeyPair rsa() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }
    }
}
