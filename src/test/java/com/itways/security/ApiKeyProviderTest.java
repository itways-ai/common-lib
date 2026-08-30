package com.itways.security;

import com.itways.common.exception.InvalidApiKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * API keys are self-describing: {@code sk_live_} +
 * AES-GCM(accHash::accEnc::userEnc::version::expiresAtEpochMillis::padding).
 * The provider is the only parser of that envelope, and the auth filter trusts
 * whatever it extracts — so the parsing contract and its failure modes are the
 * security surface.
 */
@DisplayName("ApiKeyProvider")
class ApiKeyProviderTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    /**
     * Mirrors ApiKeyProvider.PREFIX, which is private.
     *
     * Named rather than repeated inline because the prefix is the one Stripe
     * uses for live secret keys: any fixture that spells it out next to a long
     * enough payload is a Stripe key as far as secret scanners are concerned,
     * and GitHub push protection rejects the commit. Building fixtures from
     * this constant keeps them out of that shape.
     */
    private static final String PREFIX = "sk_live_";

    private final ApiKeyProvider provider = new ApiKeyProvider();

    @BeforeEach
    void primeEncryptionKey() {
        new SecurityUtils().setEncryptionKey(KEY);
    }

    private static String mintKey(String payload) {
        return PREFIX + SecurityUtils.encrypt(payload);
    }

    @Test
    @DisplayName("extracts the hash, encrypted account, version, and expiry from a well-formed key")
    void extractsParts() {
        String apiKey = mintKey("the-hash::the-enc-account::the-enc-user::2::1893456000000::padpadpad");

        assertThat(provider.getAccountIdHashedFromApiKey(apiKey)).isEqualTo("the-hash");
        assertThat(provider.getAccountIdEncryptedFromApiKey(apiKey)).isEqualTo("the-enc-account");
        assertThat(provider.getKeyVersion(apiKey)).isEqualTo(2);
        assertThat(provider.getExpiresAtEpochMillis(apiKey)).isEqualTo(1893456000000L);
    }

    @Test
    @DisplayName("an expiry slot of 0 means the key never expires, and a garbled slot degrades to 0")
    void expiryFallback() {
        assertThat(provider.getExpiresAtEpochMillis(mintKey("h::acc::user::1::0::pad"))).isZero();
        assertThat(provider.getExpiresAtEpochMillis(mintKey("h::acc::user::1::not-a-number::pad"))).isZero();
        // A payload without the slot at all (pre-expiry shape) also degrades
        // to "no expiry" rather than failing the key outright.
        assertThat(provider.getExpiresAtEpochMillis(mintKey("h::acc::user::1"))).isZero();
    }

    @Test
    @DisplayName("getUsernameFromApiKey returns the third part — still encrypted")
    void usernameIsStillEncrypted() {
        // NOTE: possible defect — the third payload part is userEnc, an AES
        // blob, and no caller decrypts it. authentication.getName() for an
        // API-key principal is therefore ciphertext, and account-service's key
        // minting feeds that blob back in as the username of new keys,
        // double-encrypting it. Pinned as current behavior.
        String apiKey = mintKey("h::acc::ENCRYPTED_USER_BLOB::1::pad");

        assertThat(provider.getUsernameFromApiKey(apiKey)).isEqualTo("ENCRYPTED_USER_BLOB");
    }

    @Test
    @DisplayName("an unparseable version degrades to 0 instead of failing the key")
    void versionFallback() {
        assertThat(provider.getKeyVersion(mintKey("h::acc::user::not-a-number::pad"))).isZero();
        assertThat(provider.getKeyVersion(mintKey("h::acc::user"))).isZero();
    }

    @Test
    @DisplayName("a missing or wrong prefix is rejected before any decryption")
    void prefixRequired() {
        assertThatThrownBy(() -> provider.getAccountIdHashedFromApiKey("pk_live_whatever"))
                .isInstanceOf(InvalidApiKeyException.class);
        assertThatThrownBy(() -> provider.getAccountIdHashedFromApiKey(null))
                .isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    @DisplayName("a prefixed key whose payload does not decrypt is rejected")
    void garbagePayloadRejected() {
        assertThatThrownBy(() -> provider.getAccountIdHashedFromApiKey(PREFIX + "bm90LXJlYWwtY2lwaGVydGV4dA=="))
                .isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    @DisplayName("a key minted under a different AES key is rejected")
    void foreignKeyMaterialRejected() {
        String apiKey = mintKey("h::acc::user::1::pad");
        new SecurityUtils().setEncryptionKey("another-32-char-key-abcdefghijkl");

        assertThatThrownBy(() -> provider.getAccountIdHashedFromApiKey(apiKey))
                .isInstanceOf(InvalidApiKeyException.class);
    }
}
