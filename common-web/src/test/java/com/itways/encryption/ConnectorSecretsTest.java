package com.itways.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** The {@code is:} cipher: one context per stored row, so a ciphertext belongs to one field of one connector. */
class ConnectorSecretsTest {

    static final String KEY_A = Base64.getEncoder().encodeToString(SealedSecretsTest.randomKey());
    static final String KEY_B = Base64.getEncoder().encodeToString(SealedSecretsTest.randomKey());
    static final String INSTANCE = "7c1e0d1a-0000-4000-8000-000000000001";
    static final String ACCOUNT = "9a4f3c2b-0000-4000-8000-000000000009";

    private final ConnectorSecrets secrets = new ConnectorSecrets(KEY_A, null);

    @Test
    void roundTripsForItsRow() {
        String sealed = secrets.seal("sk_live_abc", INSTANCE, ACCOUNT, "apiKey");

        assertThat(sealed).startsWith("is:" + secrets.currentKeyId() + ":").doesNotContain("sk_live_abc");
        assertThat(ConnectorSecrets.isSealed(sealed)).isTrue();
        assertThat(secrets.isCurrent(sealed)).isTrue();
        assertThat(secrets.open(sealed, INSTANCE, ACCOUNT, "apiKey")).isEqualTo("sk_live_abc");
        assertThat(secrets.reseal(sealed, INSTANCE, ACCOUNT, "apiKey")).isSameAs(sealed);
    }

    @Test
    void theContextIsInstanceAccountAndFieldJoinedWithPipes() {
        assertThat(ConnectorSecrets.context(INSTANCE, ACCOUNT, "apiKey"))
                .isEqualTo((INSTANCE + "|" + ACCOUNT + "|apiKey").getBytes(StandardCharsets.UTF_8));
        // The generic cipher with that context opens what the typed method sealed, and the other way round.
        SealedSecrets raw = new SealedSecrets("is:", SealedSecrets.decodeKey(KEY_A, "KEY_A"), null);
        String sealed = secrets.seal("sk_live_abc", INSTANCE, ACCOUNT, "apiKey");
        assertThat(raw.open(sealed, ConnectorSecrets.context(INSTANCE, ACCOUNT, "apiKey"))).isEqualTo("sk_live_abc");
        assertThat(secrets.open(raw.seal("x", ConnectorSecrets.context(INSTANCE, ACCOUNT, "apiKey")), INSTANCE,
                ACCOUNT, "apiKey")).isEqualTo("x");
    }

    @Test
    void aCiphertextMovedToAnotherRowFieldOrAccountDoesNotOpen() {
        String sealed = secrets.seal("sk_live_abc", INSTANCE, ACCOUNT, "apiKey");

        assertThatThrownBy(() -> secrets.open(sealed, INSTANCE, ACCOUNT, "clientSecret"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Could not open the connector secret")
                .hasMessageNotContaining("sk_live_abc");
        assertThatThrownBy(() -> secrets.open(sealed, "7c1e0d1a-0000-4000-8000-000000000002", ACCOUNT, "apiKey"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> secrets.open(sealed, INSTANCE, "other-account", "apiKey"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> secrets.open(sealed, INSTANCE.toUpperCase(), ACCOUNT, "apiKey"))
                .as("the ids are compared as text: the stored spelling is the one that opens")
                .isInstanceOf(IllegalStateException.class);
        // A current value is left alone by reseal without being opened (the rotation job only opens
        // non-current rows); one under the previous key is opened, so there the context is checked.
        assertThat(secrets.reseal(sealed, INSTANCE, ACCOUNT, "clientSecret")).isSameAs(sealed);
        assertThatThrownBy(() -> new ConnectorSecrets(KEY_B, KEY_A).reseal(sealed, INSTANCE, ACCOUNT, "clientSecret"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherKindOfSecretOrAPlainValueIsRefused() {
        String mail = new MailSecrets(KEY_A, null).seal("sk_live_abc");
        String channel = new ChannelSecrets(KEY_A, null).encrypt("sk_live_abc");

        for (String notOurs : new String[] { mail, channel, "sk_live_abc", "", null, "********" }) {
            assertThatThrownBy(() -> secrets.open(notOurs, INSTANCE, ACCOUNT, "apiKey"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Not a sealed connector secret: expected a value starting with 'is:'")
                    .hasMessageNotContaining("sk_live_abc");
            assertThat(ConnectorSecrets.isSealed(notOurs)).isFalse();
        }
        // Same key, disguised prefix: the contexts differ, so it stays shut.
        assertThatThrownBy(() -> secrets.open("is:" + mail.substring(3), INSTANCE, ACCOUNT, "apiKey"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Could not open");
        assertThatThrownBy(() -> secrets.open("is:" + channel.substring(3), INSTANCE, ACCOUNT, "apiKey"))
                .isInstanceOf(IllegalStateException.class);
        // No legacy plain values: reseal does not seal a plain value as MailSecrets does.
        assertThatThrownBy(() -> secrets.reseal("sk_live_abc", INSTANCE, ACCOUNT, "apiKey"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTamperedValueDoesNotOpen() {
        String sealed = secrets.seal("sk_live_abc", INSTANCE, ACCOUNT, "apiKey");
        String tampered = sealed.substring(0, sealed.length() - 4) + (sealed.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertThatThrownBy(() -> secrets.open(tampered, INSTANCE, ACCOUNT, "apiKey"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("sk_live_abc");
        assertThatThrownBy(() -> secrets.open("is:garbage", INSTANCE, ACCOUNT, "apiKey"))
                .hasMessageContaining("unknown key");
    }

    @Test
    void rotationKeepsOldValuesOpenableUntilResealed() {
        String underA = secrets.seal("sk_live_abc", INSTANCE, ACCOUNT, "apiKey");
        ConnectorSecrets rotated = new ConnectorSecrets(KEY_B, KEY_A);

        assertThat(rotated.open(underA, INSTANCE, ACCOUNT, "apiKey")).isEqualTo("sk_live_abc");
        assertThat(rotated.isCurrent(underA)).isFalse();
        String resealed = rotated.reseal(underA, INSTANCE, ACCOUNT, "apiKey");
        assertThat(rotated.isCurrent(resealed)).isTrue();
        assertThat(rotated.open(resealed, INSTANCE, ACCOUNT, "apiKey")).isEqualTo("sk_live_abc");
        assertThat(new ConnectorSecrets(KEY_B, null).open(resealed, INSTANCE, ACCOUNT, "apiKey"))
                .isEqualTo("sk_live_abc");
        // Once the previous key is dropped, a value still under it is refused.
        assertThatThrownBy(() -> new ConnectorSecrets(KEY_B, null).open(underA, INSTANCE, ACCOUNT, "apiKey"))
                .hasMessageContaining("Connector secret was sealed with an unknown key (" + secrets.currentKeyId() + ")");
    }

    @Test
    void theContextPartsMustBePresent() {
        assertThatThrownBy(() -> secrets.seal("x", null, ACCOUNT, "apiKey")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> secrets.seal("x", INSTANCE, " ", "apiKey")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> secrets.seal("x", INSTANCE, ACCOUNT, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> secrets.seal("x", INSTANCE, ACCOUNT, "api|Key")).hasMessageContaining("'|'");
        assertThatThrownBy(() -> secrets.seal(null, INSTANCE, ACCOUNT, "apiKey")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void refusesToStartWithoutAUsableKey() {
        assertThatThrownBy(() -> new ConnectorSecrets("", null)).hasMessageContaining("CONNECTOR_SECRETS_KEY is not set");
        assertThatThrownBy(() -> new ConnectorSecrets(null, null)).hasMessageContaining("openssl rand -base64 32");
        assertThatThrownBy(() -> new ConnectorSecrets("short", null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONNECTOR_SECRETS_KEY");
        assertThatThrownBy(() -> new ConnectorSecrets(KEY_A, "not base64!"))
                .hasMessageContaining("CONNECTOR_SECRETS_KEY_PREVIOUS");
        assertThat(new ConnectorSecrets(KEY_A, "  ").open(secrets.seal("x", INSTANCE, ACCOUNT, "f"), INSTANCE,
                ACCOUNT, "f")).isEqualTo("x");
    }
}
