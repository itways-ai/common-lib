package com.itways.activity.outbox;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itways.activity.dto.AccountActivityEvent;
import com.itways.common.correlation.RequestIds;

import lombok.extern.slf4j.Slf4j;

/**
 * Records activity events in the service's outbox table (PLT-07); the
 * {@link ActivityOutboxRelay} sends them to RabbitMQ afterwards.
 *
 * <p>
 * {@link #record} writes the row in the caller's transaction: it commits with the
 * business change and rolls back with it, so an event is never lost after a commit
 * (a broker outage or a crash only delays it) and never sent for a change that did
 * not happen. {@link #recordIndependently} is for events about an attempt that the
 * caller is about to refuse (a failed sign-in, a rejected password change): the
 * caller throws right after, and that rollback must not take the audit entry with it.
 *
 * <p>
 * Delivery is at least once: a crash between the broker's confirmation and the
 * {@code sent_at} update sends the event again, with the same {@code eventId};
 * account-service drops the repeat.
 *
 * <p>
 * An event recorded without a {@code requestId} gets the one of the request or
 * message being handled (the logging context's {@link RequestIds#MDC_KEY},
 * ARC-25), so the relay, which sends it later on its own thread, still sends
 * it with the {@code x-request-id} header.
 */
@Slf4j
public class ActivityOutbox {

    private final ActivityOutboxStore store;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate ownTransaction;
    private final ActivityOutboxRelay relay;

    /**
     * @param ownTransaction a REQUIRES_NEW template, for writes that must not join the caller's transaction
     * @param relay          woken after a commit so the event leaves at once; may be null
     */
    public ActivityOutbox(ActivityOutboxStore store, ObjectMapper objectMapper, TransactionTemplate ownTransaction,
            ActivityOutboxRelay relay) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.ownTransaction = ownTransaction;
        this.relay = relay;
    }

    /**
     * Writes the event in the current transaction, or in its own when there is none
     * (or the current one is read-only). A failure to write propagates: inside a
     * transaction it rolls the business change back with it.
     */
    public void record(AccountActivityEvent event) {
        AccountActivityEvent prepared = prepare(event);
        if (prepared == null) {
            return;
        }
        String payload = serialize(prepared);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            store.insert(prepared.getEventId(), payload);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        nudge();
                    }
                });
            }
            return;
        }
        ownTransaction.executeWithoutResult(status -> store.insert(prepared.getEventId(), payload));
        nudge();
    }

    /**
     * Writes the event in its own transaction, committed at once whatever the
     * caller's transaction does afterwards. A failure to write is logged, not
     * thrown: the caller is about to answer with its own error.
     */
    public void recordIndependently(AccountActivityEvent event) {
        AccountActivityEvent prepared = prepare(event);
        if (prepared == null) {
            return;
        }
        try {
            String payload = serialize(prepared);
            ownTransaction.executeWithoutResult(status -> store.insert(prepared.getEventId(), payload));
            nudge();
        } catch (RuntimeException e) {
            log.error("[ACTIVITY] Could not write activity event to {}: action={}, accountId={}", store.table(),
                    prepared.getAction(), prepared.getAccountId(), e);
        }
    }

    private static AccountActivityEvent prepare(AccountActivityEvent event) {
        if (event == null || event.getAccountId() == null || event.getAccountId().isBlank()) {
            log.warn("Skipping activity event: missing accountId");
            return null;
        }
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID());
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(Instant.now());
        }
        if (event.getRequestId() == null) {
            String current = MDC.get(RequestIds.MDC_KEY);
            if (RequestIds.isWellFormed(current)) {
                event.setRequestId(current);
            }
        }
        return event;
    }

    private String serialize(AccountActivityEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Activity event cannot be written as JSON: action=" + event.getAction(),
                    e);
        }
    }

    private void nudge() {
        if (relay != null) {
            relay.nudge();
        }
    }
}
