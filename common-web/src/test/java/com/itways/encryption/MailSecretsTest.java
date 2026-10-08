package com.itways.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class MailSecretsTest {

    static final String KEY_A = randomKey();
    static final String KEY_B = randomKey();

    @Test
    void roundTripsUnderTheCurrentKey() {
        MailSecrets secrets = new MailSecrets(KEY_A, null);
        String sealed = secrets.seal("smtp-p@ss");

        assertThat(sealed).startsWith("ms:").doesNotContain("smtp-p@ss");
        assertThat(MailSecrets.isSealed(sealed)).isTrue();
        assertThat(secrets.isCurrent(sealed)).isTrue();
        assertThat(secrets.open(sealed)).isEqualTo("smtp-p@ss");
    }

    @Test
    void sealIsIdempotentOnASealedValue() {
        MailSecrets secrets = new MailSecrets(KEY_A, null);
        String sealed = secrets.seal("smtp-p@ss");

        assertThat(secrets.seal(sealed)).isEqualTo(sealed);
        // Even one under a key this instance does not hold: it is passed on, not sealed twice.
        String underB = new MailSecrets(KEY_B, null).seal("other");
        assertThat(secrets.seal(underB)).isEqualTo(underB);
    }

    @Test
    void legacyPlainValuesOpenAsTheyAre() {
        MailSecrets secrets = new MailSecrets(KEY_A, null);

        assertThat(secrets.open("plain-password")).isEqualTo("plain-password");
        assertThat(MailSecrets.isSealed("plain-password")).isFalse();
        assertThat(secrets.isCurrent("plain-password")).isFalse();
        assertThat(secrets.open(null)).isNull();
    }

    @Test
    void noPasswordStaysNoPassword() {
        MailSecrets secrets = new MailSecrets(KEY_A, null);

        assertThat(secrets.seal(null)).isNull();
        assertThat(secrets.seal("")).isEmpty();
        assertThat(secrets.reseal(null)).isNull();
    }

    @Test
    void aTamperedValueDoesNotOpen() {
        MailSecrets secrets = new MailSecrets(KEY_A, null);
        String sealed = secrets.seal("smtp-p@ss");
        String tampered = sealed.substring(0, sealed.length() - 4) + (sealed.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertThatThrownBy(() -> secrets.open(tampered))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("smtp-p@ss");
        assertThatThrownBy(() -> secrets.open("ms:garbage"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("unknown key");
    }

    @Test
    void aChannelSecretUnderTheSameKeyIsNotAMailSecret() {
        String channel = new ChannelSecrets(KEY_A, null).encrypt("token");
        String disguised = "ms:" + channel.substring("cs:".length());

        assertThatThrownBy(() -> new MailSecrets(KEY_A, null).open(disguised))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rotationKeepsOldValuesOpenableUntilResealed() {
        String underA = new MailSecrets(KEY_A, null).seal("smtp-p@ss");
        MailSecrets rotated = new MailSecrets(KEY_B, KEY_A);

        assertThat(rotated.open(underA)).isEqualTo("smtp-p@ss");
        assertThat(rotated.isCurrent(underA)).isFalse();
        String resealed = rotated.reseal(underA);
        assertThat(rotated.isCurrent(resealed)).isTrue();
        assertThat(rotated.open(resealed)).isEqualTo("smtp-p@ss");
        assertThat(rotated.reseal(resealed)).isEqualTo(resealed);
        assertThat(rotated.isCurrent(rotated.reseal("plain"))).isTrue();
        // Once the previous key is dropped, a value still under it is refused.
        assertThatThrownBy(() -> new MailSecrets(KEY_B, null).open(underA))
                .hasMessageContaining("unknown key");
    }

    @Test
    void refusesToStartWithoutAUsableKey() {
        assertThatThrownBy(() -> new MailSecrets("", null)).hasMessageContaining("MAIL_SECRETS_KEY is not set");
        assertThatThrownBy(() -> new MailSecrets(null, null)).hasMessageContaining("openssl rand -base64 32");
        assertThatThrownBy(() -> new MailSecrets("short", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MailSecrets(KEY_A, "not base64!")).hasMessageContaining("PREVIOUS");
    }

    static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
