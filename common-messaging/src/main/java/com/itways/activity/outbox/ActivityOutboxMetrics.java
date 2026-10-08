package com.itways.activity.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Outbox gauges, read from the relay's last stats (every
 * {@code itways.activity.outbox.stats-interval}), never from the database on a
 * scrape; -1 while unknown, like account-service's DLQ gauge:
 * <ul>
 * <li>{@code activity.outbox.pending}: events not yet confirmed by RabbitMQ;</li>
 * <li>{@code activity.outbox.oldest.pending.age} (seconds): how long the oldest has waited.</li>
 * </ul>
 * Both are tagged with {@code table}.
 */
public class ActivityOutboxMetrics implements MeterBinder {

    private final ActivityOutboxRelay relay;

    public ActivityOutboxMetrics(ActivityOutboxRelay relay) {
        this.relay = relay;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("activity.outbox.pending", relay, r -> r.stats() == null ? -1 : r.stats().pending())
                .description("Activity events in the outbox not yet confirmed by RabbitMQ (-1: unknown)")
                .tag("table", relay.table())
                .register(registry);
        Gauge.builder("activity.outbox.oldest.pending.age", relay,
                r -> r.stats() == null ? -1 : r.stats().oldestPendingAgeSeconds())
                .description("Age of the oldest unsent activity event (-1: unknown)")
                .baseUnit("seconds")
                .tag("table", relay.table())
                .register(registry);
    }
}
