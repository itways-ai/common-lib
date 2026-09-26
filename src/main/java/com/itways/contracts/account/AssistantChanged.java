package com.itways.contracts.account;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An {@code assistant.*} event from {@link AccountEvents}: the assistant as it
 * is after the change ({@code status} ACTIVE or DISABLED; for
 * {@link AccountEvents#ASSISTANT_DELETED} as it was before). {@code change} repeats
 * the routing key so a consumer bound to {@code assistant.*} knows which one it got.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssistantChanged(
        UUID eventId,
        String change,
        String accountId,
        UUID assistantId,
        String slug,
        String status,
        boolean isDefault,
        Instant occurredAt) {
}
