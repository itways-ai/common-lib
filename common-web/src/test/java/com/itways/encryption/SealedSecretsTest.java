package com.itways.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** The one cipher behind the three secret kinds: format, contexts, keys, rotation. */
class SealedSecretsTest {

    static final byte[] KEY_A = randomKey();
    static final byte[] KEY_B = randomKey();
    static final byte[] ROW = SealedSecrets.context("7c1e0d1a-0000-4000-8000-000000000001", "acct-1", "apiKey");

    private final SealedSecrets secrets = new SealedSecrets("is:", KEY_A, null);

    @Test
    void roundTripsUnderTheCurrentKeyWithItsContext() throws Exception {
        String sealed = secrets.seal("sk_live_abc", ROW);

        assertThat(sealed).startsWith("is:" + kid(KEY_A) + ":").doesNotContain("sk_live_abc");
        assertThat(secrets.prefix()).isEqualTo("is:");
        assertThat(secrets.currentKeyId()).isEqualTo(kid(KEY_A)).hasSize(8);
        assertThat(secrets.isSealedValue(sealed)).isTrue();
        assertThat(secrets.isCurrent(sealed)).isTrue();
        assertThat(secrets.open(sealed, ROW)).isEqualTo("sk_live_abc");
        // A fresh nonce each time: two seals differ and both open.
        String again = secrets.seal("sk_live_abc", ROW);
        assertThat(again).isNotEqualTo(sealed);
        assertThat(secrets.open(again, ROW)).isEqualTo("sk_live_abc");
        // Base64 of nonce (12) + ciphertext (len) + tag (16).
        String body = sealed.substring(sealed.lastIndexOf(':') + 1);
        assertThat(Base64.getDecoder().decode(body)).hasSize(12 + "sk_live_abc".length() + 16);
    }

    @Test
    void anyTextRoundTrips() {
        for (String plain : new String[] { "", "x", "كلمة سر", "emoji 🔐 日本語", "a".repeat(5000),
                "line\nbreak\ttab\u0000nul" }) {
            assertThat(secrets.open(secrets.seal(plain, ROW), ROW)).isEqualTo(plain);
        }
    }

    @Test
    void anotherContextDoesNotOpenIt() {
        String sealed = secrets.seal("sk_live_abc", ROW);

        for (byte[] other : new byte[][] {
                SealedSecrets.context("7c1e0d1a-0000-4000-8000-000000000001", "acct-1", "clientSecret"), // field
                SealedSecrets.context("7c1e0d1a-0000-4000-8000-000000000002", "acct-1", "apiKey"), // row
                SealedSecrets.context("7c1e0d1a-0000-4000-8000-000000000001", "acct-2", "apiKey"), // account
                "mail-secret".getBytes(StandardCharsets.UTF_8), new byte[0] }) {
            assertThatThrownBy(() -> secrets.open(sealed, other)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Could not open the secret").hasMessageNotContaining("sk_live_abc");
        }
        // And a value sealed without a context does not open with one.
        String bare = secrets.seal("sk_live_abc", new byte[0]);
        assertThat(secrets.open(bare, new byte[0])).isEqualTo("sk_live_abc");
        assertThatThrownBy(() -> secrets.open(bare, ROW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTamperedOrTruncatedValueDoesNotOpen() {
        String sealed = secrets.seal("sk_live_abc", ROW);
        String head = sealed.substring(0, sealed.lastIndexOf(':') + 1);
        String tampered = sealed.substring(0, sealed.length() - 4) + (sealed.endsWith("AAAA") ? "BBBB" : "AAAA");

        for (String broken : new String[] { tampered, head + "AAAA", head, head + "not base64!!", head + "AAAAAAAA",
                sealed + "A", sealed.substring(0, sealed.length() - 1) }) {
            assertThatThrownBy(() -> secrets.open(broken, ROW)).as(broken).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Could not open the secret").hasMessageNotContaining("sk_live_abc");
        }
    }

    @Test
    void aValueWithAnotherPrefixIsRefusedBeforeAnyCrypto() {
        String mail = new SealedSecrets("ms:", KEY_A, null).seal("sk_live_abc", ROW);

        assertThat(secrets.isSealedValue(mail)).isFalse();
        for (String notOurs : new String[] { mail, "plain", "", "is", "i:" + mail.substring(3), null }) {
            assertThatThrownBy(() -> secrets.open(notOurs, ROW)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("expected a value starting with 'is:'").hasMessageNotContaining("sk_live_abc");
        }
        // The prefix is a label, not part of the binding: the context is what keeps ciphers apart.
        assertThat(secrets.open("is:" + mail.substring(3), ROW)).isEqualTo("sk_live_abc");
    }

    @Test
    void anUnknownKeyIsNamedByItsIdNeverTheValue() throws Exception {
        String underB = new SealedSecrets("is:", KEY_B, null).seal("sk_live_abc", ROW);

        assertThatThrownBy(() -> secrets.open(underB, ROW)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown key (" + kid(KEY_B) + ")").hasMessageNotContaining("sk_live_abc");
        assertThatThrownBy(() -> secrets.open("is:garbage", ROW)).hasMessageContaining("unknown key ()");
        assertThatThrownBy(() -> secrets.open("is:", ROW)).hasMessageContaining("unknown key");
    }

    @Test
    void rotationKeepsOldValuesOpenableUntilResealed() {
        String underA = secrets.seal("sk_live_abc", ROW);
        SealedSecrets rotated = new SealedSecrets("is:", KEY_B, KEY_A);

        assertThat(rotated.open(underA, ROW)).isEqualTo("sk_live_abc");
        assertThat(rotated.isCurrent(underA)).isFalse();
        assertThat(rotated.isSealedValue(underA)).isTrue();
        String resealed = rotated.reseal(underA, ROW);
        assertThat(resealed).isNotEqualTo(underA).startsWith("is:" + rotated.currentKeyId() + ":");
        assertThat(rotated.isCurrent(resealed)).isTrue();
        assertThat(rotated.open(resealed, ROW)).isEqualTo("sk_live_abc");
        assertThat(rotated.reseal(resealed, ROW)).isSameAs(resealed);
        // The reseal keeps the binding: another row still cannot open it.
        assertThatThrownBy(() -> rotated.reseal(underA, SealedSecrets.context("other", "acct-1", "apiKey")))
                .isInstanceOf(IllegalStateException.class);
        // Once the previous key is dropped, a value still under it is refused.
        assertThatThrownBy(() -> new SealedSecrets("is:", KEY_B, null).open(underA, ROW))
                .hasMessageContaining("unknown key");
        // The same key as current and previous is harmless.
        assertThat(new SealedSecrets("is:", KEY_A, KEY_A).open(underA, ROW)).isEqualTo("sk_live_abc");
    }

    @Test
    void keysMustBeThirtyTwoBytesAndThePrefixMustEndWithAColon() {
        assertThatThrownBy(() -> new SealedSecrets("is:", new byte[16], null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current key must be 32 bytes, got 16");
        assertThatThrownBy(() -> new SealedSecrets("is:", null, null)).hasMessageContaining("current key must be 32 bytes");
        assertThatThrownBy(() -> new SealedSecrets("is:", KEY_A, new byte[31]))
                .hasMessageContaining("previous key must be 32 bytes, got 31");
        for (String badPrefix : new String[] { null, "", ":", "is", "is;" }) {
            assertThatThrownBy(() -> new SealedSecrets(badPrefix, KEY_A, null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("followed by ':'");
        }
    }

    @Test
    void decodeKeyNamesTheVariableNotTheValue() {
        assertThatThrownBy(() -> SealedSecrets.decodeKey("not base64!", "SOME_KEY"))
                .isInstanceOf(IllegalStateException.class).hasMessage("SOME_KEY is not valid Base64");
        assertThatThrownBy(() -> SealedSecrets.decodeKey(Base64.getEncoder().encodeToString(new byte[16]), "SOME_KEY"))
                .hasMessageContaining("SOME_KEY must be 32 bytes").hasMessageContaining("got 16");
        assertThatThrownBy(() -> SealedSecrets.decodeKey(null, "SOME_KEY")).hasMessageContaining("SOME_KEY must be 32");
        assertThat(SealedSecrets.decodeKey(" " + Base64.getEncoder().encodeToString(KEY_A) + "\n", "SOME_KEY"))
                .isEqualTo(KEY_A);
    }

    @Test
    void theContextJoinsItsPartsWithAPipe() {
        assertThat(SealedSecrets.context("a", "b", "c")).isEqualTo("a|b|c".getBytes(StandardCharsets.UTF_8));
        assertThat(SealedSecrets.context("only")).isEqualTo("only".getBytes(StandardCharsets.UTF_8));
        assertThat(SealedSecrets.context("مفتاح", "x")).isEqualTo("مفتاح|x".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> SealedSecrets.context()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SealedSecrets.context((String[]) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SealedSecrets.context("a", null)).hasMessageContaining("null or blank");
        assertThatThrownBy(() -> SealedSecrets.context("a", " ")).hasMessageContaining("null or blank");
        assertThatThrownBy(() -> SealedSecrets.context("a|b", "c")).hasMessageContaining("'|'");
        // So (a|b, c) and (a, b|c) can never collide: both are refused.
        assertThatThrownBy(() -> SealedSecrets.context("a", "b|c")).hasMessageContaining("'|'");
    }

    @Test
    void nullArgumentsAreRefused() {
        assertThatThrownBy(() -> secrets.seal(null, ROW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> secrets.seal("x", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> secrets.open(secrets.seal("x", ROW), null)).isInstanceOf(NullPointerException.class);
        assertThat(secrets.isSealedValue(null)).isFalse();
        assertThat(secrets.isCurrent(null)).isFalse();
        assertThat(SealedSecrets.isSealed("is:x", "is:")).isTrue();
        assertThat(SealedSecrets.isSealed(null, "is:")).isFalse();
        assertThat(SealedSecrets.isSealed("is:x", null)).isFalse();
    }

    /** The three secret kinds are this cipher with a prefix and a context each, and stay apart. */
    @Test
    void theSecretKindsAreSubclassesThatStayApart() {
        String keyA = Base64.getEncoder().encodeToString(KEY_A);
        MailSecrets mail = new MailSecrets(keyA, null);
        ChannelSecrets channel = new ChannelSecrets(keyA, null);
        ConnectorSecrets connector = new ConnectorSecrets(keyA, null);

        assertThat(mail).isInstanceOf(SealedSecrets.class);
        assertThat(channel).isInstanceOf(SealedSecrets.class);
        assertThat(connector).isInstanceOf(SealedSecrets.class);
        assertThat(mail.prefix()).isEqualTo("ms:");
        assertThat(channel.prefix()).isEqualTo("cs:");
        assertThat(connector.prefix()).isEqualTo("is:");
        assertThat(mail.currentKeyId()).isEqualTo(channel.currentKeyId()).isEqualTo(connector.currentKeyId());

        String asMail = mail.seal("token");
        String asChannel = channel.encrypt("token");
        String asConnector = connector.seal("token", "inst", "acct", "apiKey");
        assertThat(asMail).startsWith("ms:");
        assertThat(asChannel).startsWith("cs:");
        assertThat(asConnector).startsWith("is:");
        assertThatThrownBy(() -> mail.open("ms:" + asChannel.substring(3))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> mail.open("ms:" + asConnector.substring(3))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> channel.decrypt("cs:" + asMail.substring(3))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> connector.open("is:" + asMail.substring(3), "inst", "acct", "apiKey"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> connector.open(asMail, "inst", "acct", "apiKey"))
                .hasMessageContaining("Not a sealed connector secret");
    }

    static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    static String kid(byte[] key) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key)).substring(0, 8);
    }
}
