package com.itways.encryption;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

@Slf4j
@Service
public class RsaService implements EncryptionService {

    // Future consideration: Store keys in a secure location (e.g., AWS Secrets
    // Manager, Azure Key Vault)
    // For now, we store keys in memory.
    // also we need to keep in mind the rotation of keys

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @Value("${rsa.private-key}")
    private String privateKeyString;

    @Value("${rsa.public-key}")
    private String publicKeyString;

    @PostConstruct
    public void loadKeys() {
        try {
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");

            // Load Private Key
            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyString);
            java.security.spec.PKCS8EncodedKeySpec privateKeySpec = new java.security.spec.PKCS8EncodedKeySpec(
                    privateKeyBytes);
            this.privateKey = keyFactory.generatePrivate(privateKeySpec);

            // Load Public Key
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyString);
            java.security.spec.X509EncodedKeySpec publicKeySpec = new java.security.spec.X509EncodedKeySpec(
                    publicKeyBytes);
            this.publicKey = keyFactory.generatePublic(publicKeySpec);

            log.info("✅ RSA Keys loaded successfully from configuration.");
        } catch (Exception e) {
            log.error("❌ Failed to load RSA Keys from configuration", e);
            throw new RuntimeException("Failed to load RSA Keys", e);
        }
    }

    // @PostConstruct
    // public void init() { // TODO: Read from properties files
    // try {
    // KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    // keyGen.initialize(2048);
    // KeyPair pair = keyGen.generateKeyPair();
    // this.privateKey = pair.getPrivate();
    // this.publicKey = pair.getPublic();
    // log.info("✅ RSA Key Pair generated successfully in-memory.");
    // } catch (Exception e) {
    // log.error("❌ Failed to generate RSA Key Pair", e);
    // throw new RuntimeException("Failed to generate RSA Key Pair", e);
    // }
    // }

    @Override
    public String encrypt(String data) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("Error encrypting data", e);
            throw new RuntimeException("Error encrypting data", e);
        }
    }

    @Override
    public String decrypt(String encryptedData) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            // Check if data is chunked (contains delimiter '|')
            if (encryptedData.contains("|")) {
                String[] chunks = encryptedData.split("\\|");
                StringBuilder decryptedData = new StringBuilder();

                for (String chunk : chunks) {
                    byte[] decodedBytes = Base64.getDecoder().decode(chunk);
                    byte[] decryptedBytes = cipher.doFinal(decodedBytes);
                    decryptedData.append(new String(decryptedBytes, StandardCharsets.UTF_8));
                }
                return decryptedData.toString();
            }

            // Standard decryption for single chunk
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error decrypting data", e);
            throw new RuntimeException("Error decrypting data", e);
        }
    }

}
