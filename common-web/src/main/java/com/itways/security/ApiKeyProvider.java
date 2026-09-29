package com.itways.security;

import com.itways.common.exception.InvalidApiKeyException;
import com.itways.security.core.ApiKeyCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reads {@code X-API-KEY} values with the key {@link SecurityUtils} is
 * configured with. The format and the rules are {@link ApiKeyCodec}'s, shared
 * with the api-gateway.
 */
@Component("apiKeyProvider")
@Slf4j
public class ApiKeyProvider {

    public String getUsernameFromApiKey(String apiKey) {
        return getPayload(apiKey).username();
    }

    public String getAccountIdHashedFromApiKey(String apiKey) {
        return getPayload(apiKey).accountIdHash();
    }

    public String getAccountIdEncryptedFromApiKey(String apiKey) {
        return getPayload(apiKey).accountIdEncrypted();
    }

    public int getKeyVersion(String apiKey) {
        try {
            return getPayload(apiKey).keyVersion();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Expiry instant carried inside the key payload, as epoch millis.
     * {@code 0} means the key never expires. A missing or unparseable slot
     * also degrades to 0 (no expiry), mirroring {@link #getKeyVersion}.
     */
    public long getExpiresAtEpochMillis(String apiKey) {
        try {
            return getPayload(apiKey).expiresAtEpochMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * The whole format check ({@link ApiKeyCodec#check}): decrypts, account
     * binding, embedded expiry against the system clock. Not the Redis
     * allow-list, which {@link ApiKeyStatusStore} answers.
     *
     * @throws RuntimeException when the encryption key is not configured
     */
    public ApiKeyCodec.Result check(String apiKey) {
        return codec().check(apiKey, System.currentTimeMillis());
    }

    private ApiKeyCodec.Payload getPayload(String apiKey) {
        if (!ApiKeyCodec.hasPrefix(apiKey)) {
            throw new InvalidApiKeyException("Invalid API Key format");
        }
        try {
            // Parts: accHash :: accEnc :: userEnc :: keyVersion :: expiresAtEpochMillis :: padding
            return codec().decode(apiKey);
        } catch (Exception e) {
            log.error("Failed to parse API key");
            throw new InvalidApiKeyException();
        }
    }

    /** Built per call: the key is static in {@link SecurityUtils} and may be set again (tests). */
    private static ApiKeyCodec codec() {
        return new ApiKeyCodec(SecurityUtils.encryptionKey());
    }
}
