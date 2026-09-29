package com.itways.security;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import com.itways.security.core.CredentialCrypto;

/**
 * AES-256-GCM encryption and SHA-256 hashing for the platform's tenant-binding
 * material (accH/accE, API-key payloads, channel credentials).
 *
 * <p>The 256-bit AES key is derived via SHA-256 from the configured
 * {@code jwt.encryption.key} secret, so the secret may be any length. Every
 * encryption uses a fresh random 12-byte IV, prepended to the ciphertext
 * before Base64 encoding; the GCM tag (128 bit) authenticates the payload, so
 * tampered or foreign ciphertext fails decryption instead of decrypting to
 * garbage.
 *
 * <p>Hard cut from the previous AES-ECB scheme: values encrypted before the
 * GCM cutover no longer decrypt and must be re-minted (API keys, stored AI
 * provider keys, channel bot/auth tokens, JWT accE claims).
 *
 * <p>The primitives themselves are {@link CredentialCrypto}'s, shared with
 * code that has no Spring (the api-gateway); this class holds the configured
 * key and adds the logging.
 */
@Component("securityUtils")
@Slf4j
public class SecurityUtils {

    private static SecretKeySpec encryptionKey;

    /**
     * Fails fast when the property is unset or blank — there is no baked-in
     * default key any more.
     */
    @Value("${jwt.encryption.key:}")
    public void setEncryptionKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "jwt.encryption.key is not configured. Set the JWT_ENCRYPTION_KEY environment variable "
                            + "(see .env.example) — the service cannot start without it.");
        }
        log.debug("Initializing SecurityUtils with derived AES-256 key");
        encryptionKey = deriveKey(key);
    }

    private static SecretKeySpec deriveKey(String secret) {
        return CredentialCrypto.deriveKey(secret);
    }

    public static String hash(String value) {
        return CredentialCrypto.hash(value);
    }

    public static String encrypt(String value) {
        if (value == null) {
            log.warn("SecurityUtils.encrypt called with null value");
            return null;
        }
        requireKey();
        try {
            return CredentialCrypto.encrypt(encryptionKey, value);
        } catch (Exception e) {
            // Never log the plaintext — it is secret material by definition here.
            log.error("Encryption failed: {}", e.getClass().getSimpleName());
            throw new RuntimeException("Error encrypting value", e);
        }
    }

    public static String decrypt(String encryptedValue) {
        if (encryptedValue == null)
            return null;
        requireKey();
        try {
            return CredentialCrypto.decrypt(encryptionKey, encryptedValue);
        } catch (Exception e) {
            // Deliberately do NOT log the ciphertext (or anything derived from
            // it): pre-GCM behavior leaked encrypted credentials into logs.
            log.error("Decryption failed: {}", e.getClass().getSimpleName());
            throw new RuntimeException("Error decrypting value", e);
        }
    }

    /**
     * The configured AES key, for {@code ApiKeyProvider}'s {@code ApiKeyCodec}.
     * Throws, as {@link #decrypt} does, when it is not initialised.
     */
    static SecretKeySpec encryptionKey() {
        requireKey();
        return encryptionKey;
    }

    private static void requireKey() {
        if (encryptionKey == null) {
            log.error("SecurityUtils encryptionKey is NOT initialized! Ensure SecurityUtils is a "
                    + "Spring-managed bean and jwt.encryption.key is set.");
            throw new RuntimeException("Encryption key not initialized");
        }
    }
}
