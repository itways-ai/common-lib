package com.itways.contracts.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An account's default AI provider with its <strong>decrypted</strong> key.
 *
 * <p>
 * Served by account-service ({@code GET /api/account/ai-configs/internal/active})
 * to speech-service only, for the account named in the caller's own credential.
 * Never log it, cache it outside the platform's shared cache, or return it to a
 * browser.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InternalAiConfig(
        String provider,
        String apiKey,
        String model) {

    /** Keeps the key out of logs and exception messages. */
    @Override
    public String toString() {
        return "InternalAiConfig[provider=" + provider + ", model=" + model + ", apiKey=***]";
    }
}
