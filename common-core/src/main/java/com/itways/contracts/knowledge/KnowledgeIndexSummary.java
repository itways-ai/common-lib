package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One knowledge index as lists show it: its name, who it belongs to and, since 2.2.0, what it
 * holds and whether ingestion is still running.
 *
 * <p>
 * Served by journey-service ({@code /api/knowledge-base/indexes}) and passed
 * through by conversation-service's public API. Several indexes may share a name: one
 * per assistant and one shared. References name an index, and the name resolves
 * in the referrer's scope (the assistant's own first, then the shared one).
 *
 * @param assistantId the owning assistant; null when shared
 * @param shared      true when every assistant of the account may use it
 * @param rowCount    how many passages it holds (kept from 2.1.0; equals {@code passages})
 * @param sources     how many sources (files, pages, typed rows) it has (2.2.0)
 * @param passages    how many passages it holds (2.2.0)
 * @param pending     how many of them still wait for their embedding (2.2.0)
 * @param status      {@link #STATUS_READY}, {@link #STATUS_PROCESSING} (a source is queued or being
 *                    read) or {@link #STATUS_FAILED} (a source failed) (2.2.0)
 * @param updatedAt   when a source or passage last changed, UTC (2.2.0)
 * @param legacyName  true when the name predates {@link KnowledgeIndexName}'s rule (2.2.0)
 * @param ignoreTerms words and phrases (brand names, say) left out of the passage and query
 *                    text before either is embedded, set with {@link IndexSettingsRequest}; never
 *                    null, empty when none (2.2.0, the embedding switch)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeIndexSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        UUID assistantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean shared,
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long rowCount,
        long sources,
        long passages,
        long pending,
        String status,
        LocalDateTime updatedAt,
        boolean legacyName,
        List<String> ignoreTerms) {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_FAILED = "FAILED";

    /** A missing list (an older payload) reads as none; null entries are dropped. */
    public KnowledgeIndexSummary {
        ignoreTerms = ignoreTerms == null ? List.of() : ignoreTerms.stream().filter(Objects::nonNull).toList();
    }

    /** The shape before ignore terms: none. */
    public KnowledgeIndexSummary(Long id, String name, UUID assistantId, boolean shared, String description,
            long rowCount, long sources, long passages, long pending, String status, LocalDateTime updatedAt,
            boolean legacyName) {
        this(id, name, assistantId, shared, description, rowCount, sources, passages, pending, status, updatedAt,
                legacyName, List.of());
    }

    /**
     * The 2.1.0 shape: {@code passages = rowCount}, no sources or pending passages, READY, no
     * update time, and {@code legacyName} from {@link KnowledgeIndexName#isLegacy}.
     */
    public KnowledgeIndexSummary(Long id, String name, UUID assistantId, boolean shared, String description,
            long rowCount) {
        this(id, name, assistantId, shared, description, rowCount, 0, rowCount, 0, STATUS_READY, null,
                KnowledgeIndexName.isLegacy(name));
    }
}
