package com.itways.activity.outbox;

import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code itways.activity.outbox.*}: the transactional outbox for activity events
 * (PLT-07). Off unless {@code enabled=true}; a service that turns it on also
 * creates the table in its own Flyway migration and names it in {@code table}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "itways.activity.outbox")
public class ActivityOutboxProperties {

    private static final Pattern TABLE_NAME = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    /** Writes activity events to the outbox table instead of sending them straight to RabbitMQ. */
    private boolean enabled;

    /**
     * The outbox table, unique per service because the services share one database
     * ({@code auth_activity_outbox}, {@code journey_activity_outbox}, ...).
     */
    private String table;

    /** Whether this instance runs the relay; writes to the table happen either way. */
    private boolean relayEnabled = true;

    /** How often the relay looks for unsent rows (it is also woken after each commit). */
    private Duration pollInterval = Duration.ofSeconds(2);

    /** Rows claimed and sent per round. */
    private int batchSize = 100;

    /** How long the broker has to confirm a batch. */
    private Duration confirmTimeout = Duration.ofSeconds(10);

    /** Wait before the second attempt of a row; doubled per failed attempt up to {@code max-backoff}. */
    private Duration initialBackoff = Duration.ofSeconds(1);

    /** Longest wait between two attempts of a row. */
    private Duration maxBackoff = Duration.ofMinutes(5);

    /** How long sent rows are kept before they are deleted. */
    private Duration retention = Duration.ofDays(7);

    /** How often sent rows past their retention are deleted. */
    private Duration cleanupInterval = Duration.ofHours(1);

    /** How often the pending count and the oldest pending age are read for metrics and health. */
    private Duration statsInterval = Duration.ofSeconds(15);

    /** The health indicator reports WARNING when the oldest unsent row is older than this. */
    private Duration lagWarning = Duration.ofMinutes(5);

    /** The table name, checked: it is written into SQL, so only lower-case identifiers are accepted. */
    public String requireTable() {
        if (table == null || !TABLE_NAME.matcher(table).matches()) {
            throw new IllegalStateException("itways.activity.outbox.table must name the service's outbox table "
                    + "(lower-case letters, digits and underscores, e.g. auth_activity_outbox); got: " + table);
        }
        return table;
    }
}
