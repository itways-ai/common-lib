package com.itways.activity.outbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itways.activity.dto.AccountActivityEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends the outbox rows to RabbitMQ (PLT-07).
 *
 * <p>
 * Each round claims up to {@code batch-size} due rows with
 * {@code FOR UPDATE SKIP LOCKED}, publishes them with publisher confirms and marks
 * them sent, all in one transaction: another instance's relay skips the locked
 * rows, so two relays never send the same row. When the broker does not confirm,
 * every row of the batch gets {@code attempts + 1} and a {@code next_attempt_at}
 * of {@code initial-backoff * 2^attempts} (at most {@code max-backoff}).
 *
 * <p>
 * Runs on its own single thread, not on {@code @Scheduled}: the services do not
 * all enable scheduling, and enabling it here would start their other
 * {@code @Scheduled} methods. It polls every {@code poll-interval} and is woken
 * after each commit that wrote an event ({@link #nudge}). Sent rows older than
 * {@code retention} are deleted every {@code cleanup-interval}. The pending count
 * and the oldest pending age are read every {@code stats-interval} for the
 * metrics and the health indicator.
 */
@Slf4j
public class ActivityOutboxRelay implements SmartLifecycle {

    private final ActivityOutboxStore store;
    private final ActivityBatchSender sender;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;
    private final ActivityOutboxProperties properties;

    private final AtomicBoolean nudged = new AtomicBoolean();
    private volatile ScheduledExecutorService executor;
    private volatile ActivityOutboxStore.Stats stats;
    private volatile boolean lagging;

    public ActivityOutboxRelay(ActivityOutboxStore store, ActivityBatchSender sender, TransactionTemplate transaction,
            ObjectMapper objectMapper, ActivityOutboxProperties properties) {
        this.store = store;
        this.sender = sender;
        this.transaction = transaction;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    // ───────────────────────────── lifecycle ─────────────────────────────

    @Override
    public void start() {
        ScheduledExecutorService started = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "activity-outbox-" + store.table());
            thread.setDaemon(true);
            return thread;
        });
        if (properties.isRelayEnabled()) {
            long poll = properties.getPollInterval().toMillis();
            started.scheduleWithFixedDelay(this::relaySafely, poll, poll, TimeUnit.MILLISECONDS);
            long cleanup = properties.getCleanupInterval().toMillis();
            started.scheduleWithFixedDelay(this::cleanupSafely, cleanup, cleanup, TimeUnit.MILLISECONDS);
        }
        long statsEvery = properties.getStatsInterval().toMillis();
        started.scheduleWithFixedDelay(this::refreshStats, 0, statsEvery, TimeUnit.MILLISECONDS);
        executor = started;
        log.info("[ACTIVITY] Outbox {} started (relay {})", store.table(),
                properties.isRelayEnabled() ? "on" : "off");
    }

    @Override
    public void stop() {
        ScheduledExecutorService running = executor;
        executor = null;
        if (running == null) {
            return;
        }
        running.shutdown();
        try {
            if (!running.awaitTermination(5, TimeUnit.SECONDS)) {
                running.shutdownNow();
            }
        } catch (InterruptedException e) {
            running.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return executor != null;
    }

    // ───────────────────────────── relay ─────────────────────────────

    /** Sends what is due soon, on the relay thread; several nudges in a row cost one round. */
    public void nudge() {
        ScheduledExecutorService running = executor;
        if (running == null || !properties.isRelayEnabled() || !nudged.compareAndSet(false, true)) {
            return;
        }
        try {
            running.execute(() -> {
                nudged.set(false);
                relaySafely();
            });
        } catch (RuntimeException e) {
            nudged.set(false); // shutting down
        }
    }

    /**
     * Sends due rows until none are left or a batch fails.
     *
     * @return how many rows were sent
     */
    public int relayPending() {
        int total = 0;
        while (true) {
            int sent = relayOnce();
            total += sent;
            if (sent < properties.getBatchSize()) {
                return total;
            }
        }
    }

    /**
     * One batch, in one transaction.
     *
     * @return how many rows were sent (0 when none were due or the batch failed)
     */
    public int relayOnce() {
        Integer sent = transaction.execute(status -> {
            List<ActivityOutboxStore.Row> rows = store.claimDue(properties.getBatchSize());
            if (rows.isEmpty()) {
                return 0;
            }
            List<AccountActivityEvent> events = new ArrayList<>(rows.size());
            List<ActivityOutboxStore.Row> sendable = new ArrayList<>(rows.size());
            for (ActivityOutboxStore.Row row : rows) {
                try {
                    events.add(objectMapper.readValue(row.payload(), AccountActivityEvent.class));
                    sendable.add(row);
                } catch (Exception e) {
                    store.markFailed(row.id(), properties.getMaxBackoff(), "Unreadable payload: " + e.getMessage());
                    log.error("[ACTIVITY] Outbox row {} in {} cannot be read; retried every {}", row.id(),
                            store.table(), properties.getMaxBackoff(), e);
                }
            }
            try {
                sender.send(events);
            } catch (RuntimeException e) {
                String error = e.getClass().getSimpleName() + ": " + e.getMessage();
                for (ActivityOutboxStore.Row row : sendable) {
                    store.markFailed(row.id(), backoff(row.attempts()), error);
                }
                log.warn("[ACTIVITY] Outbox {}: {} event(s) not confirmed by RabbitMQ, retrying later ({})",
                        store.table(), sendable.size(), error);
                return 0;
            }
            List<UUID> ids = sendable.stream().map(ActivityOutboxStore.Row::id).toList();
            store.markSent(ids);
            return ids.size();
        });
        return sent == null ? 0 : sent;
    }

    /** The wait before the next attempt of a row that has failed {@code attempts} times before this one. */
    Duration backoff(int attempts) {
        Duration max = properties.getMaxBackoff();
        Duration delay = properties.getInitialBackoff();
        for (int i = 0; i < attempts && delay.compareTo(max) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(max) > 0 ? max : delay;
    }

    private void relaySafely() {
        try {
            relayPending();
        } catch (RuntimeException e) {
            log.warn("[ACTIVITY] Outbox {} relay round failed: {}", store.table(), e.getMessage());
        }
    }

    // ───────────────────────────── housekeeping ─────────────────────────────

    /** @return how many sent rows were deleted */
    public int cleanup() {
        int deleted = store.deleteSentBefore(properties.getRetention());
        if (deleted > 0) {
            log.debug("[ACTIVITY] Outbox {}: deleted {} sent row(s) older than {}", store.table(), deleted,
                    properties.getRetention());
        }
        return deleted;
    }

    private void cleanupSafely() {
        try {
            cleanup();
        } catch (RuntimeException e) {
            log.warn("[ACTIVITY] Outbox {} cleanup failed: {}", store.table(), e.getMessage());
        }
    }

    /** Reads the pending count and the oldest pending age; unknown (null) while the database cannot be asked. */
    public void refreshStats() {
        try {
            ActivityOutboxStore.Stats read = store.stats();
            stats = read;
            boolean nowLagging = read.oldestPendingAgeSeconds() > properties.getLagWarning().toSeconds();
            if (nowLagging && !lagging) {
                log.warn("[ACTIVITY] Outbox {}: {} event(s) unsent, the oldest for {} s", store.table(),
                        read.pending(), read.oldestPendingAgeSeconds());
            } else if (!nowLagging && lagging) {
                log.info("[ACTIVITY] Outbox {} caught up", store.table());
            }
            lagging = nowLagging;
        } catch (RuntimeException e) {
            stats = null;
            log.debug("[ACTIVITY] Could not read outbox {} stats: {}", store.table(), e.getMessage());
        }
    }

    /** The last stats read, or null when unknown. */
    public ActivityOutboxStore.Stats stats() {
        return stats;
    }

    public ActivityOutboxProperties properties() {
        return properties;
    }

    public String table() {
        return store.table();
    }
}
