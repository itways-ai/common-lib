package com.itways.encryption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RsaService decrypts the client-side-encrypted passwords that arrive at
 * registration, login, and password reset. The chunked ({@code |}-delimited)
 * path exists because RSA-2048 caps a single block at 245 bytes — long
 * payloads arrive as multiple chunks.
 */
@DisplayName("RsaService")
class RsaServiceTest {

    private RsaService rsaService;

    @BeforeEach
    void buildService() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        rsaService = new RsaService();
        ReflectionTestUtils.setField(rsaService, "privateKeyString",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        ReflectionTestUtils.setField(rsaService, "publicKeyString",
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        rsaService.loadKeys();
    }

    @Test
    @DisplayName("encrypt/decrypt round-trips, and ciphertext is never the plaintext")
    void roundTrip() {
        String ciphertext = rsaService.encrypt("S3cure!Password");

        assertThat(ciphertext).isNotEqualTo("S3cure!Password");
        assertThat(rsaService.decrypt(ciphertext)).isEqualTo("S3cure!Password");
    }

    @Test
    @DisplayName("chunked ciphertext joined with | decrypts to the concatenated plaintext")
    void chunkedDecryption() {
        String chunked = rsaService.encrypt("first-half::") + "|" + rsaService.encrypt("second-half");

        assertThat(rsaService.decrypt(chunked)).isEqualTo("first-half::second-half");
    }

    @Test
    @DisplayName("tampered ciphertext throws instead of returning garbage")
    void tamperedCiphertext() {
        assertThatThrownBy(() -> rsaService.decrypt("bm90LXJlYWwtY2lwaGVydGV4dA=="))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("unloadable key material fails startup loudly, not lazily at first use")
    void badKeyMaterialFailsLoad() {
        RsaService broken = new RsaService();
        ReflectionTestUtils.setField(broken, "privateKeyString", "not-base64-key-material");
        ReflectionTestUtils.setField(broken, "publicKeyString", "not-base64-key-material");

        assertThatThrownBy(broken::loadKeys).isInstanceOf(RuntimeException.class);
    }
}
