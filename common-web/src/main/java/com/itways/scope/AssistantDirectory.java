package com.itways.scope;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What services read about assistants, from account-service's
 * {@code assistant_directory}: the read-only view account-service publishes as
 * its stable read contract in the database the services share.
 *
 * <p>
 * Plain SQL rather than a JPA entity, so no service becomes a second author of
 * account-service's schema. A database read rather than a call to
 * account-service: it runs inside the caller's write transaction, needs no
 * forwarded credential, and some reads happen on every turn of a conversation.
 *
 * <p>
 * Every lookup is scoped by account: an assistant is never resolved by id alone.
 */
@Slf4j
public class AssistantDirectory {

    public static final String ACTIVE = "ACTIVE";

    private static final String COLUMNS = "id, account_id, name, status, is_default, knowledge_indexes";

    private final JdbcTemplate jdbcTemplate;

    public AssistantDirectory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** One row of the directory. {@code knowledgeIndexes} is never null. */
    public record Entry(UUID id, String accountId, String name, String status, boolean isDefault,
            List<String> knowledgeIndexes) {

        public boolean active() {
            return ACTIVE.equals(status);
        }
    }

    /**
     * Whether the assistant belongs to the account. Fails closed: this guards
     * writes, so an unreadable view is an error, never a "yes".
     */
    public boolean belongsTo(UUID assistantId, String accountId) {
        if (assistantId == null || accountId == null) {
            return false;
        }
        Integer found = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM assistant_directory WHERE id = ? AND account_id = ?",
                Integer.class, assistantId, accountId);
        return found != null && found > 0;
    }

    /** The assistant, if it belongs to the account. Fails closed. */
    public Optional<Entry> find(UUID assistantId, String accountId) {
        if (assistantId == null || accountId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM assistant_directory WHERE id = ? AND account_id = ?",
                (rs, i) -> entry(rs), assistantId, accountId).stream().findFirst();
    }

    /** The account's default assistant, if it has one. Fails closed. */
    public Optional<Entry> defaultOf(String accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM assistant_directory WHERE account_id = ? AND is_default = true",
                (rs, i) -> entry(rs), accountId).stream().findFirst();
    }

    /**
     * The knowledge indexes the assistant was given by name. Fails soft: an
     * unreadable view reads as "no indexes", which only narrows what the
     * assistant can draw on.
     */
    public List<String> knowledgeIndexes(UUID assistantId, String accountId) {
        try {
            return find(assistantId, accountId).map(Entry::knowledgeIndexes).orElse(List.of());
        } catch (DataAccessException e) {
            log.debug("Could not read knowledge indexes for assistant {}: {}", assistantId, e.getMessage());
            return List.of();
        }
    }

    private static Entry entry(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Entry(rs.getObject("id", UUID.class), rs.getString("account_id"), rs.getString("name"),
                rs.getString("status"), rs.getBoolean("is_default"), split(rs.getString("knowledge_indexes")));
    }

    /** Comma-separated, as account-service stores them; null or blank means none. */
    static List<String> split(String knowledgeIndexes) {
        if (knowledgeIndexes == null || knowledgeIndexes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(knowledgeIndexes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}
