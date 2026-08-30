package com.itways.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

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
 */
@Component
@Slf4j
public class SecurityUtils {

    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String hash(String value) {
        if (value == null)
            return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing value", e);
        }
    }

    public static String encrypt(String value) {
        if (value == null) {
            log.warn("SecurityUtils.encrypt called with null value");
            return null;
        }
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
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
            byte[] decoded = Base64.getDecoder().decode(encryptedValue);
            if (decoded.length <= IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Ciphertext too short");
            }
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Deliberately do NOT log the ciphertext (or anything derived from
            // it): pre-GCM behavior leaked encrypted credentials into logs.
            log.error("Decryption failed: {}", e.getClass().getSimpleName());
            throw new RuntimeException("Error decrypting value", e);
        }
    }

    private static void requireKey() {
        if (encryptionKey == null) {
            log.error("SecurityUtils encryptionKey is NOT initialized! Ensure SecurityUtils is a "
                    + "Spring-managed bean and jwt.encryption.key is set.");
            throw new RuntimeException("Encryption key not initialized");
        }
    }
}
