package com.itways.security.core;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;

/** Test-only keys and credentials, minted the way auth-service, channels-service and account-service do. */
final class TestTokens {

    static final String SECRET = "test-only-security-core-secret";
    static final SecretKeySpec AES = CredentialCrypto.deriveKey(SECRET);
    static final String ACCOUNT_ID = "4242";
    static final String USERNAME = "qa.user@example.test";

    static final KeyPair PLATFORM = rsa();
    static final KeyPair WEBHOOK = rsa();
    static final KeyPair WEBHOOK_PREVIOUS = rsa();
    static final KeyPair FOREIGN = rsa();

    private TestTokens() {
    }

    /** JwtTokenProvider.generateAccessToken: RS256, no kid, no type. */
    static String accessToken(PrivateKey key, Instant issuedAt, Duration ttl) {
        return Jwts.builder().subject(USERNAME)
                .claim("role", "USER").claim("accH", CredentialCrypto.hash(ACCOUNT_ID))
                .claim("accE", encrypt(ACCOUNT_ID))
                .issuedAt(Date.from(issuedAt)).expiration(Date.from(issuedAt.plus(ttl)))
                .signWith(key, Jwts.SIG.RS256).compact();
    }

    static String accessToken() {
        return accessToken(PLATFORM.getPrivate(), Instant.now(), Duration.ofHours(1));
    }

    static String refreshToken() {
        return typed(PLATFORM.getPrivate(), null, TokenVerifier.TYPE_REFRESH);
    }

    /** channels-service ChannelWebhookTokenProvider.generate. */
    static String webhookToken(PrivateKey key, String keyId) {
        return typed(key, keyId, TokenVerifier.TYPE_CHANNEL_WEBHOOK);
    }

    static String typed(PrivateKey key, String keyId, Object type) {
        Instant now = Instant.now();
        JwtBuilder token = Jwts.builder().id(UUID.randomUUID().toString()).subject("channel:c1")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(Duration.ofDays(7))))
                .claim("accH", CredentialCrypto.hash(ACCOUNT_ID)).claim("accE", encrypt(ACCOUNT_ID))
                .claim("channelId", "c1");
        if (type != null) {
            token.claim("type", type);
        }
        if (keyId != null) {
            token.header().keyId(keyId).and();
        }
        return token.signWith(key, Jwts.SIG.RS256).compact();
    }

    /** account-service ApiKeyService.generateKey; {@code expiresAtMillis} 0 = never. */
    static String apiKey(long expiresAtMillis) {
        String base = String.join("::", CredentialCrypto.hash(ACCOUNT_ID), encrypt(ACCOUNT_ID), encrypt(USERNAME),
                "3", String.valueOf(expiresAtMillis));
        return apiKeyFromPayload(base + "::" + "x".repeat(300), AES);
    }

    static String apiKeyFromPayload(String payload, SecretKeySpec key) {
        try {
            return ApiKeyCodec.PREFIX + CredentialCrypto.encrypt(key, payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String encrypt(String value) {
        return encrypt(value, AES);
    }

    static String encrypt(String value, SecretKeySpec key) {
        try {
            return CredentialCrypto.encrypt(key, value);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String decrypt(String value) {
        try {
            return CredentialCrypto.decrypt(AES, value);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
