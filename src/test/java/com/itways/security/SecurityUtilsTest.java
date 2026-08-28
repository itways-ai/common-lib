package com.itways.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SecurityUtils carries the AES key binding accountIds into JWTs and API keys
 * (the accH/accE pair). It is a static-state utility primed through an instance
 * setter, so each test primes the key explicitly — the same move any consumer
 * test must make, and itself a documented testability hazard of the design.
 */
@DisplayName("SecurityUtils")
class SecurityUtilsTest {

    /** Same shape as the production default: 32 ASCII chars = AES-256 key. */
    private static final String TEST_KEY = "0123456789abcdef0123456789abcdef";

    @BeforeEach
    void primeKey() {
        new SecurityUtils().setEncryptionKey(TEST_KEY);
    }

    @Test
    @DisplayName("hash is deterministic SHA-256, Base64-encoded")
    void hashDeterministic() {
        String first = SecurityUtils.hash("AIUS000000000001");
        String second = SecurityUtils.hash("AIUS000000000001");

        assertThat(first).isEqualTo(second).isNotEqualTo(SecurityUtils.hash("AIUS000000000002"));
        // SHA-256 → 32 bytes → 44 Base64 chars. The tenant-binding check
        // compares these strings verbatim, so the encoding is contract.
        assertThat(first).hasSize(44);
    }

    @Test
    @DisplayName("hash of null is null rather than an exception")
    void hashNull() {
        assertThat(SecurityUtils.hash(null)).isNull();
    }

    @Test
    @DisplayName("encrypt/decrypt round-trips the original value")
    void roundTrip() {
        String ciphertext = SecurityUtils.encrypt("AIUS000000000001");

        assertThat(ciphertext).isNotEqualTo("AIUS000000000001");
        assertThat(SecurityUtils.decrypt(ciphertext)).isEqualTo("AIUS000000000001");
    }

    @Test
    @DisplayName("encryption is deterministic — same input, same ciphertext")
    void deterministicCiphertext() {
        // Plain AES with no IV (ECB). Deterministic output is what makes the
        // accE claim stable across logins; it is also a known weakness of the
        // scheme worth being conscious of when rotating the design.
        assertThat(SecurityUtils.encrypt("value")).isEqualTo(SecurityUtils.encrypt("value"));
    }

    @Test
    @DisplayName("decrypting tampered ciphertext throws instead of returning garbage")
    void tamperedCiphertext() {
        String ciphertext = SecurityUtils.encrypt("value");
        String tampered = ciphertext.substring(0, ciphertext.length() - 4) + "AAAA";

        assertThatThrownBy(() -> SecurityUtils.decrypt(tampered))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("null pass-throughs and a changed key are both handled explicitly")
    void nullAndKeyChange() {
        assertThat(SecurityUtils.encrypt(null)).isNull();
        assertThat(SecurityUtils.decrypt(null)).isNull();

        // Ciphertext from one key must not decrypt under another — this is the
        // failure mode of the jwt.encryption.key / jwt.encryption-key config
        // mismatch between auth-service and account-service.
        String ciphertext = SecurityUtils.encrypt("value");
        new SecurityUtils().setEncryptionKey("another-32-char-key-abcdefghijkl");
        assertThatThrownBy(() -> SecurityUtils.decrypt(ciphertext))
                .isInstanceOf(RuntimeException.class);
    }
}
