package com.itways.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Values sealed by common-lib 2.2.0's {@code MailSecrets} and
 * {@code ChannelSecrets}, when each held its own copy of the cipher, must open
 * with the 2.3.0 classes built on {@link SealedSecrets}: same prefix, same key
 * id, same associated data, byte for byte. The fixture
 * {@code encryption/sealed-secrets-2.2.0.properties} was produced once from the
 * unchanged 2.2.0 classes with two fixed test keys and is never regenerated; if
 * this test fails, stored passwords and tokens would stop opening after an
 * upgrade.
 */
class SealedSecretsGoldenVectorsTest {

    private static final String FIXTURE = "/encryption/sealed-secrets-2.2.0.properties";
    private static final byte[] MAIL_CONTEXT = "mail-secret".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CHANNEL_CONTEXT = "channel-secret".getBytes(StandardCharsets.UTF_8);

    private static Properties vectors;
    private static String keyA;
    private static String keyB;
    private static int count;

    @BeforeAll
    static void load() throws IOException {
        vectors = new Properties();
        try (InputStream in = SealedSecretsGoldenVectorsTest.class.getResourceAsStream(FIXTURE)) {
            assertThat(in).as(FIXTURE).isNotNull();
            vectors.load(in);
        }
        keyA = vectors.getProperty("key.a");
        keyB = vectors.getProperty("key.b");
        count = Integer.parseInt(vectors.getProperty("plain.count"));
    }

    @Test
    void theFixtureIsTheOneProducedFromTheOldClasses() {
        assertThat(count).isEqualTo(8);
        assertThat(vectors).hasSize(43);
        assertThat(keyA).isEqualTo("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
        assertThat(keyB).isEqualTo("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        // The plaintexts cover ASCII, Arabic, emoji and a long value; a fixture written with the
        // wrong charset would have turned the non-Latin ones into question marks.
        assertThat(plain(0)).isEqualTo("smtp-p@ss");
        assertThat(plain(2)).isEmpty();
        assertThat(plain(5)).isEqualTo("كلمة سر عربية");
        assertThat(plain(6)).contains("🔐").contains("ümlauts").contains("日本語");
        assertThat(plain(7)).hasSize(600);
        // The key ids the old classes derived (first 8 hex of SHA-256) are the ones the new ones derive.
        assertThat(new MailSecrets(keyA, null).currentKeyId()).isEqualTo(vectors.getProperty("kid.a"))
                .isEqualTo(new ChannelSecrets(keyA, null).currentKeyId());
        assertThat(new MailSecrets(keyB, null).currentKeyId()).isEqualTo(vectors.getProperty("kid.b"));
        IntStream.range(0, count).forEach(n -> {
            if (!plain(n).isEmpty()) {
                assertThat(mail("a", n)).startsWith("ms:" + vectors.getProperty("kid.a") + ":");
                assertThat(mail("b", n)).startsWith("ms:" + vectors.getProperty("kid.b") + ":");
            }
            assertThat(channel("a", n)).startsWith("cs:" + vectors.getProperty("kid.a") + ":");
            assertThat(channel("b", n)).startsWith("cs:" + vectors.getProperty("kid.b") + ":");
        });
    }

    @Test
    void mailPasswordsSealedBeforeTheRefactoringOpen() {
        MailSecrets underA = new MailSecrets(keyA, null);
        MailSecrets rotatedToA = new MailSecrets(keyA, keyB);
        MailSecrets underB = new MailSecrets(keyB, null);

        IntStream.range(0, count).filter(n -> !plain(n).isEmpty()) // MailSecrets.seal("") is "": nothing was sealed
                .forEach(n -> {
                    String plain = plain(n);
                    assertThat(underA.open(mail("a", n))).as("mail.a." + n).isEqualTo(plain);
                    assertThat(underA.isCurrent(mail("a", n))).isTrue();
                    assertThat(MailSecrets.isSealed(mail("a", n))).isTrue();
                    assertThat(underA.reseal(mail("a", n))).isEqualTo(mail("a", n));

                    // After a rotation the old value opens, is not current, and reseals to one the new key alone opens.
                    assertThat(rotatedToA.open(mail("b", n))).as("mail.b." + n).isEqualTo(plain);
                    assertThat(rotatedToA.isCurrent(mail("b", n))).isFalse();
                    String resealed = rotatedToA.reseal(mail("b", n));
                    assertThat(rotatedToA.isCurrent(resealed)).isTrue();
                    assertThat(underA.open(resealed)).isEqualTo(plain);
                    assertThat(underB.open(mail("b", n))).isEqualTo(plain);
                    assertThatThrownBy(() -> underA.open(mail("b", n))).hasMessageContaining("unknown key");
                });
    }

    @Test
    void channelTokensEncryptedBeforeTheRefactoringDecrypt() {
        ChannelSecrets underA = new ChannelSecrets(keyA, null);
        ChannelSecrets rotatedToA = new ChannelSecrets(keyA, keyB);
        ChannelSecrets underB = new ChannelSecrets(keyB, null);

        IntStream.range(0, count).forEach(n -> {
            String plain = plain(n);
            assertThat(underA.decrypt(channel("a", n))).as("channel.a." + n).isEqualTo(plain);
            assertThat(underA.isCurrent(channel("a", n))).isTrue();

            assertThat(rotatedToA.decrypt(channel("b", n))).as("channel.b." + n).isEqualTo(plain);
            assertThat(rotatedToA.isCurrent(channel("b", n))).isFalse();
            assertThat(underA.decrypt(rotatedToA.encrypt(rotatedToA.decrypt(channel("b", n))))).isEqualTo(plain);
            assertThat(underB.decrypt(channel("b", n))).isEqualTo(plain);
            assertThatThrownBy(() -> underA.decrypt(channel("b", n))).hasMessageContaining("unknown key");
        });
    }

    /**
     * The wire format and the associated data are what they were: the generic
     * cipher with the old prefix and the old context string opens the old values,
     * values sealed today have the same shape, and the contexts still keep the
     * kinds apart.
     */
    @Test
    void theWireFormatAndTheContextsAreByteIdentical() {
        byte[] bytesA = SealedSecrets.decodeKey(keyA, "key.a");
        SealedSecrets rawMail = new SealedSecrets("ms:", bytesA, null);
        SealedSecrets rawChannel = new SealedSecrets("cs:", bytesA, null);
        MailSecrets mail = new MailSecrets(keyA, null);
        ChannelSecrets channel = new ChannelSecrets(keyA, null);
        ConnectorSecrets connector = new ConnectorSecrets(keyA, null);

        IntStream.range(0, count).forEach(n -> {
            String plain = plain(n);
            if (!plain.isEmpty()) {
                assertThat(rawMail.open(mail("a", n), MAIL_CONTEXT)).isEqualTo(plain);
                String fresh = mail.seal(plain);
                assertThat(fresh).hasSameSizeAs(mail("a", n)).startsWith(mail("a", n).substring(0, "ms:".length() + 9));
                assertThat(rawMail.open(fresh, MAIL_CONTEXT)).isEqualTo(plain);
                // The old value under any other context, or disguised as another kind, stays shut.
                assertThatThrownBy(() -> rawMail.open(mail("a", n), CHANNEL_CONTEXT))
                        .isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(() -> channel.decrypt("cs:" + mail("a", n).substring(3)))
                        .isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(() -> connector.open("is:" + mail("a", n).substring(3), "inst", "acct", "password"))
                        .isInstanceOf(IllegalStateException.class);
            }
            assertThat(rawChannel.open(channel("a", n), CHANNEL_CONTEXT)).isEqualTo(plain);
            String fresh = channel.encrypt(plain);
            assertThat(fresh).hasSameSizeAs(channel("a", n));
            assertThat(rawChannel.open(fresh, CHANNEL_CONTEXT)).isEqualTo(plain);
            assertThatThrownBy(() -> rawChannel.open(channel("a", n), MAIL_CONTEXT))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> mail.open("ms:" + channel("a", n).substring(3)))
                    .isInstanceOf(IllegalStateException.class);
        });
    }

    private static String plain(int n) {
        return vectors.getProperty("plain." + n);
    }

    private static String mail(String key, int n) {
        return vectors.getProperty("mail." + key + "." + n);
    }

    private static String channel(String key, int n) {
        return vectors.getProperty("channel." + key + "." + n);
    }
}
