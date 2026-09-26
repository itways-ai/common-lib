package com.itways.contracts.account;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@link AccountEvents#AI_CONFIG_CHANGED}: the account's AI provider configs were
 * saved, deleted or given a new default. Carries no key material; a holder of a
 * cached {@link InternalAiConfig} drops it and fetches again when needed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiConfigChanged(
        UUID eventId,
        String accountId,
        Instant occurredAt) {
}
