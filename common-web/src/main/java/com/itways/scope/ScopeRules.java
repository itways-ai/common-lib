package com.itways.scope;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The one set of rules for which assistant a row belongs to, applied the same
 * way by every service that stores per-assistant rows (journeys, templates,
 * knowledge indexes, channels).
 *
 * <ul>
 * <li><b>Create.</b> {@code shared=true} makes the row shared. Otherwise the
 * request's assistant, else the console's selected assistant (header). With
 * neither, the request is refused: silently sharing a new row would expose it to
 * every other assistant, which is invisible until the wrong one uses it.</li>
 * <li><b>Update.</b> {@code shared=true} makes it shared; an assistant moves it;
 * neither keeps the current scope.</li>
 * <li><b>Ownership.</b> The assistant must be the caller's; another account's
 * answers 404. Otherwise a tenant could reserve names under another tenant's
 * assistant.</li>
 * </ul>
 */
public class ScopeRules {

    private final AssistantDirectory directory;
    private final JdbcTemplate jdbcTemplate;

    public ScopeRules(AssistantDirectory directory, JdbcTemplate jdbcTemplate) {
        this.directory = directory;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The scope of a new row.
     *
     * @param requested the assistant the request names, or null
     * @param shared    the request's {@code shared} flag, or null
     * @param selected  the console's selected assistant ({@link ScopeHeaders#ASSISTANT}), or null
     */
    public AssistantScope forCreate(UUID requested, Boolean shared, UUID selected, String accountId) {
        if (Boolean.TRUE.equals(shared)) {
            return AssistantScope.SHARED;
        }
        UUID assistantId = requested != null ? requested : selected;
        if (assistantId == null) {
            throw ScopeErrors.scopeRequired();
        }
        return AssistantScope.of(requireOwn(assistantId, accountId));
    }

    /**
     * The owner of a new row that can never be shared (a channel answers as
     * exactly one assistant).
     */
    public UUID ownerForCreate(UUID requested, Boolean shared, UUID selected, String accountId, String what) {
        if (Boolean.TRUE.equals(shared)) {
            throw ScopeErrors.sharedNotAllowed(what);
        }
        return forCreate(requested, false, selected, accountId).assistantId();
    }

    /** The scope after an update; an omitted assistant keeps {@code current}. */
    public AssistantScope forUpdate(UUID requested, Boolean shared, AssistantScope current, String accountId) {
        if (Boolean.TRUE.equals(shared)) {
            return AssistantScope.SHARED;
        }
        if (requested == null || requested.equals(current.assistantId())) {
            return current;
        }
        return AssistantScope.of(requireOwn(requested, accountId));
    }

    /** The assistant, if it is the caller's; 404 otherwise. Null passes through. */
    public UUID requireOwn(UUID assistantId, String accountId) {
        if (assistantId != null && !directory.belongsTo(assistantId, accountId)) {
            throw ScopeErrors.assistantNotFound(assistantId);
        }
        return assistantId;
    }

    /**
     * Holds a Postgres advisory lock on {@code namespace:accountId} until the
     * current transaction ends, so a uniqueness check within a scope and the
     * write after it are one step. Every scope rule is per account, so a
     * per-account lock is exact. Must run inside the write transaction.
     */
    public void lockAccount(String namespace, String accountId) {
        jdbcTemplate.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(hashtext(?))", Integer.class,
                namespace + ":" + accountId);
    }
}
