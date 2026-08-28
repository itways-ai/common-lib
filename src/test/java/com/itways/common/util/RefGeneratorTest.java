package com.itways.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RefGenerator mints OTP transaction refs, password-reset tokens, and OAuth
 * state values. The silent length clamp is the important pin: callers asking
 * for 64 random chars get 32 — the API lies about entropy.
 */
@DisplayName("RefGenerator")
class RefGeneratorTest {

    private final RefGenerator generator = new RefGenerator();

    @Test
    @DisplayName("prefixes the reference and upper-cases the random part")
    void prefixAndCase() {
        String reference = generator.generate("TXN", 16);

        assertThat(reference).startsWith("TXN").hasSize(3 + 16);
        assertThat(reference.substring(3)).isUpperCase();
    }

    @Test
    @DisplayName("a requested length beyond 32 silently clamps to 32")
    void lengthClamps() {
        // NOTE: possible defect — PasswordServiceImpl asks for 64 chars of
        // reset-token entropy and receives 32 (one UUID's hex). 128 bits is
        // still plenty, but the API silently halves what was requested.
        assertThat(generator.generate("PWD_RESET_", 64))
                .hasSize("PWD_RESET_".length() + 32);
    }

    @Test
    @DisplayName("references are unique across invocations")
    void uniqueness() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            assertThat(seen.add(generator.generate("T", 32))).isTrue();
        }
    }
}
