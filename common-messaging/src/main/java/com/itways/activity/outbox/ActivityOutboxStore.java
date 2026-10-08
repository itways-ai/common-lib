package com.itways.activity.outbox;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The SQL of the outbox table (PostgreSQL). Every statement runs in whatever
 * transaction is current on the calling thread.
 *
 * <pre>
 * id              uuid PRIMARY KEY          the event id
 * payload         jsonb NOT NULL            the AccountActivityEvent
 * created_at      timestamptz NOT NULL      default now()
 * attempts        integer NOT NULL          default 0
 * next_attempt_at timestamptz NOT NULL      default now()
 * sent_at         timestamptz               null until the broker confirmed it
 * last_error      varchar(500)
 * </pre>
 */
public class ActivityOutboxStore {

    /** A claimed row. */
    public record Row(UUID id, String payload, int attempts) {
    }

    /** Pending rows and the age of the oldest, in seconds (0 when none). */
    public record Stats(long pending, long oldestPendingAgeSeconds) {
    }

    private static final int MAX_ERROR = 500;

    private final JdbcTemplate jdbc;
    private final String table;

    public ActivityOutboxStore(JdbcTemplate jdbc, String table) {
        this.jdbc = jdbc;
        this.table = table;
    }

    public String table() {
        return table;
    }

    public void insert(UUID id, String payload) {
        jdbc.update("INSERT INTO " + table + " (id, payload) VALUES (?, CAST(? AS jsonb))", id, payload);
    }

    /**
     * Locks up to {@code limit} rows that are due; rows another relay holds are
     * skipped, so several instances never send the same row at the same time.
     */
    public List<Row> claimDue(int limit) {
        return jdbc.query("SELECT id, payload::text AS payload, attempts FROM " + table
                + " WHERE sent_at IS NULL AND next_attempt_at <= now()"
                + " ORDER BY next_attempt_at, created_at LIMIT ? FOR UPDATE SKIP LOCKED",
                (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("payload"), rs.getInt("attempts")),
                limit);
    }

    public void markSent(List<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("UPDATE " + table + " SET sent_at = now(), last_error = NULL WHERE id = ?",
                ids.stream().map(id -> new Object[] { id }).toList());
    }

    public void markFailed(UUID id, Duration retryIn, String error) {
        jdbc.update("UPDATE " + table + " SET attempts = attempts + 1,"
                + " next_attempt_at = now() + (? * interval '1 millisecond'), last_error = ? WHERE id = ?",
                retryIn.toMillis(), truncate(error), id);
    }

    /** @return how many sent rows were deleted */
    public int deleteSentBefore(Duration retention) {
        return jdbc.update("DELETE FROM " + table
                + " WHERE sent_at IS NOT NULL AND sent_at < now() - (? * interval '1 millisecond')",
                retention.toMillis());
    }

    public Stats stats() {
        return jdbc.queryForObject("SELECT count(*) AS pending,"
                + " COALESCE(EXTRACT(EPOCH FROM (now() - min(created_at))), 0)::bigint AS oldest"
                + " FROM " + table + " WHERE sent_at IS NULL",
                (rs, n) -> new Stats(rs.getLong("pending"), rs.getLong("oldest")));
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR ? error : error.substring(0, MAX_ERROR);
    }
}
