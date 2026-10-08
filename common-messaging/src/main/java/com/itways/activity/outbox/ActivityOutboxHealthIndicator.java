package com.itways.activity.outbox;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * {@code activityOutbox}: UP while the outbox keeps up; {@code WARNING} when the
 * oldest unsent event is older than {@code itways.activity.outbox.lag-warning}
 * (RabbitMQ unreachable for that long, or the relay stuck); UNKNOWN before the
 * first read or while the database cannot be asked.
 *
 * <p>
 * {@code WARNING} is not in Spring Boot's status order, so it never changes the
 * aggregate status: the service stays ready (its readiness group lists its own
 * components), and nothing is lost while the rows wait. It shows on the component
 * for whoever looks at {@code /actuator/health}.
 */
public class ActivityOutboxHealthIndicator extends AbstractHealthIndicator {

    public static final Status WARNING = new Status("WARNING",
            "Activity events are waiting longer than itways.activity.outbox.lag-warning");

    private final ActivityOutboxRelay relay;

    public ActivityOutboxHealthIndicator(ActivityOutboxRelay relay) {
        super("Activity outbox check failed");
        this.relay = relay;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        ActivityOutboxStore.Stats stats = relay.stats();
        builder.withDetail("table", relay.table());
        if (stats == null) {
            builder.unknown();
            return;
        }
        builder.withDetail("pending", stats.pending())
                .withDetail("oldestPendingAgeSeconds", stats.oldestPendingAgeSeconds());
        if (stats.oldestPendingAgeSeconds() > relay.properties().getLagWarning().toSeconds()) {
            builder.status(WARNING);
        } else {
            builder.up();
        }
    }
}
