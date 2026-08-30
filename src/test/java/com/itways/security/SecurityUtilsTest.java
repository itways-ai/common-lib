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
 *
 * <p>Since the AES-GCM cutover the scheme is non-deterministic (fresh random
 * IV per encryption) and authenticated (tampering fails the GCM tag), and the
 * 256-bit key is derived from the configured secret via SHA-256 — so the
 * secret no longer has to be exactly 32 characters.
 */
@DisplayName("SecurityUtils")
class SecurityUtilsTest {

    /** Same shape as the old production default: 32 ASCII chars. Any length works now. */
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
    @DisplayName("encryption is non-deterministic — fresh IV per call, both decrypt")
    void nonDeterministicCiphertext() {
        // GCM prepends a random 12-byte IV to every ciphertext, so the same
        // plaintext encrypts differently every time. Nothing may compare
        // ciphertexts for equality any more — the accE claim differs between
        // logins, and equality of accounts is asserted via accH instead.
        String first = SecurityUtils.encrypt("value");
        String second = SecurityUtils.encrypt("value");

        assertThat(first).isNotEqualTo(second);
        assertThat(SecurityUtils.decrypt(first)).isEqualTo("value");
        assertThat(SecurityUtils.decrypt(second)).isEqualTo("value");
    }

    @Test
    @DisplayName("decrypting tampered ciphertext fails the GCM tag instead of returning garbage")
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

        // Ciphertext from one key must not decrypt under another — the GCM tag
        // check refuses it. This is why all services must agree on the single
        // canonical jwt.encryption.key property.
        String ciphertext = SecurityUtils.encrypt("value");
        new SecurityUtils().setEncryptionKey("another-32-char-key-abcdefghijkl");
        assertThatThrownBy(() -> SecurityUtils.decrypt(ciphertext))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("the secret may be any length — a 256-bit key is derived via SHA-256")
    void keyDerivedFromArbitraryLengthSecret() {
        new SecurityUtils().setEncryptionKey("short");

        String ciphertext = SecurityUtils.encrypt("value");
        assertThat(SecurityUtils.decrypt(ciphertext)).isEqualTo("value");
    }

    @Test
    @DisplayName("an unset or blank secret fails fast at startup with a pointer to the env var")
    void blankSecretFailsFast() {
        SecurityUtils instance = new SecurityUtils();

        assertThatThrownBy(() -> instance.setEncryptionKey(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_ENCRYPTION_KEY");
        assertThatThrownBy(() -> instance.setEncryptionKey(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_ENCRYPTION_KEY");
    }
}
