package com.itways.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.itways.security.SecurityUtils;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChannelSecretsTest {

    static final String KEY_A = randomKey();
    static final String KEY_B = randomKey();

    @BeforeAll
    static void platformKey() {
        new SecurityUtils().setEncryptionKey("unit-test-secret");
    }

    @Test
    void roundTripsUnderTheCurrentKey() {
        ChannelSecrets secrets = new ChannelSecrets(KEY_A, null);
        String sealed = secrets.encrypt("123456:bot-token");

        assertThat(sealed).startsWith("cs:").doesNotContain("bot-token");
        assertThat(secrets.isCurrent(sealed)).isTrue();
        assertThat(secrets.decrypt(sealed)).isEqualTo("123456:bot-token");
    }

    @Test
    void aTamperedValueDoesNotDecrypt() {
        ChannelSecrets secrets = new ChannelSecrets(KEY_A, null);
        String sealed = secrets.encrypt("token");
        String tampered = sealed.substring(0, sealed.length() - 4) + (sealed.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertThatThrownBy(() -> secrets.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("token");
    }

    @Test
    void rotationKeepsOldValuesReadableUntilReEncrypted() {
        String underA = new ChannelSecrets(KEY_A, null).encrypt("token");
        ChannelSecrets rotated = new ChannelSecrets(KEY_B, KEY_A);

        assertThat(rotated.decrypt(underA)).isEqualTo("token");
        assertThat(rotated.isCurrent(underA)).isFalse();
        assertThat(rotated.isCurrent(rotated.encrypt("token"))).isTrue();
        // Once the previous key is dropped, a value still under it is refused.
        assertThatThrownBy(() -> new ChannelSecrets(KEY_B, null).decrypt(underA))
                .hasMessageContaining("unknown key");
    }

    @Test
    void legacyValuesAreReadWithThePlatformKey() {
        String legacy = SecurityUtils.encrypt("token");
        ChannelSecrets secrets = new ChannelSecrets(KEY_A, null);

        assertThat(secrets.decrypt(legacy)).isEqualTo("token");
        assertThat(secrets.isCurrent(legacy)).isFalse();
    }

    @Test
    void refusesToStartWithoutAUsableKey() {
        assertThatThrownBy(() -> new ChannelSecrets("", null)).hasMessageContaining("CHANNEL_SECRETS_KEY is not set");
        assertThatThrownBy(() -> new ChannelSecrets("short", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ChannelSecrets(KEY_A, "not base64!")).hasMessageContaining("PREVIOUS");
    }

    static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
