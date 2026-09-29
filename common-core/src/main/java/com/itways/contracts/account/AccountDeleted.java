package com.itways.contracts.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * {@link AccountEvents#ACCOUNT_DELETED}: the account is gone, and every service
 * that keeps rows for it should delete them. Sent once the deletion has
 * committed in account-service; it replaces the {@code assistant.deleted} events
 * of the account's assistants, which are not sent one by one.
 *
 * <p>
 * The same account may be announced more than once (a retried deletion), so a
 * consumer must be idempotent: deleting by {@code accountId} is.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountDeleted(
        UUID eventId,
        String accountId,
        Instant occurredAt) {
}
