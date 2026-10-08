package com.itways.security.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class CredentialCryptoTest {

    @Test
    void hashIsBase64OfSha256() {
        // FIPS 180-2 test vector for "abc".
        assertThat(CredentialCrypto.hash("abc")).isEqualTo("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=");
        assertThat(CredentialCrypto.hash(null)).isNull();
    }

    @Test
    void theAesKeyIsTheSha256OfTheSecret() {
        SecretKeySpec key = CredentialCrypto.deriveKey("abc");

        assertThat(key.getAlgorithm()).isEqualTo("AES");
        assertThat(Base64.getEncoder().encodeToString(key.getEncoded())).isEqualTo(CredentialCrypto.hash("abc"));
    }

    @Test
    void encryptionRoundTripsWithAFreshIvEachTime() throws Exception {
        SecretKeySpec key = CredentialCrypto.deriveKey("secret");
        String first = CredentialCrypto.encrypt(key, "4242 – ünïcode");
        String second = CredentialCrypto.encrypt(key, "4242 – ünïcode");

        assertThat(first).isNotEqualTo(second);
        assertThat(CredentialCrypto.decrypt(key, first)).isEqualTo("4242 – ünïcode");
        // 12-byte IV + UTF-8 plaintext + 16-byte tag.
        assertThat(Base64.getDecoder().decode(first))
                .hasSize(12 + "4242 – ünïcode".getBytes(StandardCharsets.UTF_8).length + 16);
    }

    @Test
    void foreignTamperedOrShortCiphertextFails() throws Exception {
        SecretKeySpec key = CredentialCrypto.deriveKey("secret");
        String value = CredentialCrypto.encrypt(key, "4242");
        byte[] bytes = Base64.getDecoder().decode(value);
        bytes[bytes.length - 1] ^= 1;

        assertThatThrownBy(() -> CredentialCrypto.decrypt(CredentialCrypto.deriveKey("other"), value))
                .isInstanceOf(GeneralSecurityException.class);
        assertThatThrownBy(() -> CredentialCrypto.decrypt(key, Base64.getEncoder().encodeToString(bytes)))
                .isInstanceOf(GeneralSecurityException.class);
        assertThatThrownBy(() -> CredentialCrypto.decrypt(key, Base64.getEncoder().encodeToString(new byte[12])))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Ciphertext too short");
        assertThatThrownBy(() -> CredentialCrypto.decrypt(key, "not base64!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicKeysParseStrictlyOrLeniently() throws Exception {
        String base64 = Base64.getEncoder().encodeToString(TestTokens.PLATFORM.getPublic().getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n" + base64.substring(0, 64) + "\n" + base64.substring(64)
                + "\n-----END PUBLIC KEY-----\n";

        assertThat(PublicKeys.parse(base64)).isEqualTo(TestTokens.PLATFORM.getPublic());
        assertThat(PublicKeys.fromConfig("RSA_PUBLIC_KEY", pem)).isEqualTo(TestTokens.PLATFORM.getPublic());
        assertThat(PublicKeys.fromConfig("RSA_PUBLIC_KEY", " ")).isNull();
        assertThat(PublicKeys.fromConfig("RSA_PUBLIC_KEY", null)).isNull();
        assertThatThrownBy(() -> PublicKeys.parse(pem)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnreadableConfiguredKeyIsNamedButNeverQuoted() {
        String broken = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAbroken";

        assertThatThrownBy(() -> PublicKeys.fromConfig("RSA_PUBLIC_KEY", broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RSA_PUBLIC_KEY")
                .hasMessageNotContaining(broken);
    }
}
