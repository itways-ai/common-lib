package com.itways.security.core;

import static com.itways.security.core.TestTokens.ACCOUNT_ID;
import static com.itways.security.core.TestTokens.AES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.itways.security.core.ApiKeyCodec.Payload;
import com.itways.security.core.ApiKeyCodec.Result;
import com.itways.security.core.ApiKeyCodec.Status;

class ApiKeyCodecTest {

    private static final long NOW = 1_900_000_000_000L;

    private final ApiKeyCodec codec = ApiKeyCodec.fromSecret(TestTokens.SECRET);

    @Test
    void aKeyWithoutExpiryIsValidAndCarriesItsSlots() {
        Result result = codec.check(TestTokens.apiKey(0L), NOW);

        assertThat(result.status()).isEqualTo(Status.VALID);
        assertThat(result.isValid()).isTrue();
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        Payload payload = result.payload();
        assertThat(payload.accountIdHash()).isEqualTo(CredentialCrypto.hash(ACCOUNT_ID));
        assertThat(TestTokens.decrypt(payload.username())).isEqualTo(TestTokens.USERNAME);
        assertThat(payload.keyVersion()).isEqualTo(3);
        assertThat(payload.expiresAtEpochMillis()).isZero();
        assertThat(payload.size()).isEqualTo(6);
    }

    @Test
    void theExpiryIsInclusiveOfItsOwnInstant() {
        String key = TestTokens.apiKey(NOW);

        assertThat(codec.check(key, NOW - 1).status()).isEqualTo(Status.VALID);
        Result expired = codec.check(key, NOW);
        assertThat(expired.status()).isEqualTo(Status.EXPIRED);
        assertThat(expired.accountId()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void aMissingOrUnreadableExpiryOrVersionMeansNone() {
        String hash = CredentialCrypto.hash(ACCOUNT_ID);
        String noSlots = TestTokens.apiKeyFromPayload(String.join("::", hash, TestTokens.encrypt(ACCOUNT_ID), "u"),
                AES);
        String garbage = TestTokens.apiKeyFromPayload(
                String.join("::", hash, TestTokens.encrypt(ACCOUNT_ID), "u", "v2", "soon"), AES);

        for (String key : new String[] { noSlots, garbage }) {
            Result result = codec.check(key, NOW);
            assertThat(result.status()).isEqualTo(Status.VALID);
            assertThat(result.payload().keyVersion()).isZero();
            assertThat(result.payload().expiresAtEpochMillis()).isZero();
        }
    }

    @Test
    void aTamperedKeyIsInvalid() {
        String key = TestTokens.apiKey(0L);
        int at = key.length() / 2;
        String tampered = key.substring(0, at) + (key.charAt(at) == 'A' ? 'B' : 'A') + key.substring(at + 1);

        assertThat(codec.check(tampered, NOW).status()).isEqualTo(Status.INVALID);
    }

    @Test
    void malformedKeysAreInvalid() {
        for (String key : new String[] { null, "", "sk_live_", "sk_live_x", "pk_live_abc", "not-a-key",
                "sk_live_" + Base64.getEncoder().encodeToString(new byte[40]) }) {
            Result result = codec.check(key, NOW);
            assertThat(result.status()).as(String.valueOf(key)).isEqualTo(Status.INVALID);
            assertThat(result.payload()).isNull();
            assertThat(result.accountId()).isNull();
        }
    }

    @Test
    void aKeyEncryptedWithAnotherSecretIsInvalid() {
        SecretKeySpec other = CredentialCrypto.deriveKey("another-secret");
        String payload = String.join("::", CredentialCrypto.hash(ACCOUNT_ID), TestTokens.encrypt(ACCOUNT_ID, other),
                "u", "1", "0", "pad");

        assertThat(codec.check(TestTokens.apiKeyFromPayload(payload, other), NOW).status())
                .isEqualTo(Status.INVALID);
    }

    @Test
    void aBrokenAccountBindingIsInvalid() {
        String payload = String.join("::", CredentialCrypto.hash("9999"), TestTokens.encrypt(ACCOUNT_ID), "u", "1",
                "0", "pad");
        String undecryptableAccE = String.join("::", CredentialCrypto.hash(ACCOUNT_ID), "bm90LWNpcGhlcnRleHQ=",
                "u");

        assertThat(codec.check(TestTokens.apiKeyFromPayload(payload, AES), NOW).status())
                .isEqualTo(Status.INVALID);
        assertThat(codec.check(TestTokens.apiKeyFromPayload(undecryptableAccE, AES), NOW).status())
                .isEqualTo(Status.INVALID);
    }

    @Test
    void fewerThanThreeSlotsIsInvalid() {
        String payload = String.join("::", CredentialCrypto.hash(ACCOUNT_ID), TestTokens.encrypt(ACCOUNT_ID));

        assertThat(codec.check(TestTokens.apiKeyFromPayload(payload, AES), NOW).status())
                .isEqualTo(Status.INVALID);
    }

    @Test
    void decodeChecksThePrefixAndTheEncryptionOnly() throws Exception {
        String twoSlots = TestTokens.apiKeyFromPayload("a::b", AES);

        assertThat(codec.decode(twoSlots).size()).isEqualTo(2);
        assertThatThrownBy(() -> codec.decode("pk_live_abc")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid API Key format");
        assertThatThrownBy(() -> codec.decode(twoSlots.replace("sk_live_", "sk_live_A")))
                .isInstanceOf(Exception.class);
        assertThat(ApiKeyCodec.hasPrefix("sk_live_x")).isTrue();
        assertThat(ApiKeyCodec.hasPrefix(null)).isFalse();
    }
}
