package com.itways.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtTokenProvider is the platform's only token issuer and verifier. The
 * rejection cases are the security surface: an expired, tampered, or
 * foreign-key token must all fail verification, and the dev-mode ephemeral
 * key fallback must never produce tokens another instance accepts.
 */
@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private KeyPair keyPair;
    private JwtTokenProvider provider;

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static JwtTokenProvider providerWith(KeyPair pair, long accessMs, long refreshMs) throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "privateKeyStr",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        ReflectionTestUtils.setField(provider, "publicKeyStr",
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        ReflectionTestUtils.setField(provider, "accessExpiration", accessMs);
        ReflectionTestUtils.setField(provider, "refreshExpiration", refreshMs);
        provider.init();
        return provider;
    }

    @BeforeEach
    void buildProvider() throws Exception {
        keyPair = rsaKeyPair();
        provider = providerWith(keyPair, 3_600_000, 604_800_000);
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(keyPair.getPublic()).build()
                .parseSignedClaims(token).getPayload();
    }

    @Nested
    @DisplayName("issuance")
    class Issuance {

        @Test
        @DisplayName("an access token carries subject, custom claims, and an expiry")
        void accessTokenShape() {
            String token = provider.generateAccessToken("user@example.com",
                    Map.of("role", "USER", "accH", "hash-value", "accE", "enc-value"));

            Claims claims = parse(token);
            assertThat(claims.getSubject()).isEqualTo("user@example.com");
            assertThat(claims.get("role")).isEqualTo("USER");
            assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
            assertThat(provider.getAccountIdHashedFromToken(token)).isEqualTo("hash-value");
            assertThat(provider.getAccountIdEncryptedFromToken(token)).isEqualTo("enc-value");
        }

        @Test
        @DisplayName("a refresh token is typed REFRESH — the claim refresh endpoints must check")
        void refreshTokenTyped() {
            String token = provider.generateRefreshToken("user@example.com");

            assertThat(parse(token).get("type")).isEqualTo("REFRESH");
            // And an access token carries no such type: the two are
            // distinguishable, which is what makes checking the claim possible.
            String access = provider.generateToken("user@example.com", "USER");
            assertThat(parse(access).get("type")).isNull();
        }
    }

    @Nested
    @DisplayName("verification")
    class Verification {

        @Test
        @DisplayName("a freshly issued token validates and yields its subject")
        void validToken() {
            String token = provider.generateToken("user@example.com", "USER");

            assertThat(provider.validateToken(token)).isTrue();
            assertThat(provider.getUsernameFromToken(token)).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("an expired token is rejected")
        void expiredToken() throws Exception {
            JwtTokenProvider expiredIssuer = providerWith(keyPair, -1_000, -1_000);
            String token = expiredIssuer.generateToken("user@example.com", "USER");

            assertThat(provider.validateToken(token)).isFalse();
        }

        @Test
        @DisplayName("a tampered payload is rejected")
        void tamperedPayload() {
            String token = provider.generateToken("user@example.com", "USER");
            String[] parts = token.split("\\.");
            String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    new String(Base64.getUrlDecoder().decode(parts[1]))
                            .replace("USER", "ADMIN").getBytes());

            assertThat(provider.validateToken(parts[0] + "." + forgedPayload + "." + parts[2]))
                    .isFalse();
        }

        @Test
        @DisplayName("a token signed by a different key is rejected")
        void foreignKeyRejected() throws Exception {
            JwtTokenProvider otherIssuer = providerWith(rsaKeyPair(), 3_600_000, 3_600_000);
            String foreign = otherIssuer.generateToken("user@example.com", "USER");

            assertThat(provider.validateToken(foreign)).isFalse();
        }

        @Test
        @DisplayName("garbage and null tokens are rejected, never an exception")
        void garbageRejected() {
            assertThat(provider.validateToken("not.a.token")).isFalse();
            assertThat(provider.validateToken("")).isFalse();
            assertThat(provider.validateToken(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("ephemeral dev-key fallback")
    class EphemeralFallback {

        @Test
        @DisplayName("blank key config generates a working throwaway pair")
        void generatesWorkingPair() throws Exception {
            JwtTokenProvider ephemeral = new JwtTokenProvider();
            ReflectionTestUtils.setField(ephemeral, "privateKeyStr", "");
            ReflectionTestUtils.setField(ephemeral, "publicKeyStr", "");
            ReflectionTestUtils.setField(ephemeral, "accessExpiration", 60_000L);
            ephemeral.init();

            String token = ephemeral.generateToken("user@example.com", "USER");
            assertThat(ephemeral.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("two ephemeral instances reject each other's tokens — the silent multi-instance failure")
        void instancesDisagree() throws Exception {
            // In any deployment with more than one JVM, blank key config means
            // every instance mints its own pair: tokens issued by one are 401s
            // everywhere else, with nothing in the logs but a parse failure.
            JwtTokenProvider first = new JwtTokenProvider();
            ReflectionTestUtils.setField(first, "privateKeyStr", "");
            ReflectionTestUtils.setField(first, "publicKeyStr", "");
            ReflectionTestUtils.setField(first, "accessExpiration", 60_000L);
            first.init();

            JwtTokenProvider second = new JwtTokenProvider();
            ReflectionTestUtils.setField(second, "privateKeyStr", "");
            ReflectionTestUtils.setField(second, "publicKeyStr", "");
            ReflectionTestUtils.setField(second, "accessExpiration", 60_000L);
            second.init();

            assertThat(second.validateToken(first.generateToken("user@example.com", "USER")))
                    .isFalse();
        }
    }
}
