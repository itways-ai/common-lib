package com.itways.security.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The crypto of the platform's tenant-binding material (the {@code accH} and
 * {@code accE} claims, API-key payloads, channel credentials).
 *
 * <ul>
 * <li>{@link #hash}: Base64 of the SHA-256 of the UTF-8 bytes.</li>
 * <li>{@link #deriveKey}: the 256-bit AES key is the SHA-256 of the UTF-8
 * secret ({@code JWT_ENCRYPTION_KEY}), so the secret may be any length.</li>
 * <li>{@link #encrypt}/{@link #decrypt}: AES-256-GCM, a fresh random 12-byte IV
 * prepended to the ciphertext and 128-bit tag, the whole Base64-encoded. The
 * tag authenticates the payload, so tampered or foreign ciphertext fails
 * instead of decrypting to garbage.</li>
 * </ul>
 *
 * <p>
 * Nothing here logs; exceptions never carry the plaintext.
 */
public final class CredentialCrypto {

    public static final int IV_LENGTH_BYTES = 12;
    public static final int TAG_LENGTH_BITS = 128;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CredentialCrypto() {
    }

    /** The AES key for {@code secret}: its SHA-256. */
    public static SecretKeySpec deriveKey(String secret) {
        return new SecretKeySpec(sha256(secret.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    /** Base64 of the SHA-256 of the UTF-8 bytes; {@code null} for {@code null}. */
    public static String hash(String value) {
        if (value == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(sha256(value.getBytes(StandardCharsets.UTF_8)));
    }

    /** Base64(IV || ciphertext+tag) of the UTF-8 bytes of {@code value}. */
    public static String encrypt(SecretKeySpec key, String value) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

        ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
        buffer.put(iv);
        buffer.put(ciphertext);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    /**
     * The plaintext of {@link #encrypt}'s output.
     *
     * @throws IllegalArgumentException when the value is not Base64 or too short
     *                                  to hold an IV
     * @throws GeneralSecurityException when it does not authenticate under
     *                                  {@code key} (tampered, foreign or
     *                                  truncated)
     */
    public static String decrypt(SecretKeySpec key, String encryptedValue) throws GeneralSecurityException {
        byte[] decoded = Base64.getDecoder().decode(encryptedValue);
        if (decoded.length <= IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Ciphertext too short");
        }
        ByteBuffer buffer = ByteBuffer.wrap(decoded);
        byte[] iv = new byte[IV_LENGTH_BYTES];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
