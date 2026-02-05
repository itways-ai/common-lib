package com.itways.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SecurityUtils {

    private static String encryptionKey;

    @Value("${jwt.encryption.key:mySup3rS3cr3tAccIdK3y12345678901}")
    public void setEncryptionKey(String key) {
        log.debug("Initializing SecurityUtils with encryption key");
        encryptionKey = key;
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
        if (encryptionKey == null) {
            log.error(
                    "SecurityUtils encryptionKey is NOT initialized! Ensure SecurityUtils is a Spring-managed bean and scanBasePackages includes its package.");
            throw new RuntimeException("Encryption key not initialized");
        }
        try {
            SecretKeySpec secretKey = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("Encryption failed for value: {}", value, e);
            throw new RuntimeException("Error encrypting value: " + e.getMessage(), e);
        }
    }

    public static String decrypt(String encryptedValue) {
        if (encryptedValue == null)
            return null;
        if (encryptionKey == null) {
            log.error("SecurityUtils encryptionKey is NOT initialized!");
            throw new RuntimeException("Encryption key not initialized");
        }
        try {
            SecretKeySpec secretKey = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedValue)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed for value: {}", encryptedValue, e);
            throw new RuntimeException("Error decrypting value: " + e.getMessage(), e);
        }
    }
}
