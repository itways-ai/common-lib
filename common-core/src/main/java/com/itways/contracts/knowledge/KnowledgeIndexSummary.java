package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
 * @param passagesByLocale how many passages carry each language tag ({@code "en"}, {@code "ar"}, as
 *                    stored); untagged passages are not counted. What a search reads to tell whether
 *                    a question is in another language than the index (cross-language search,
 *                    2.3.0); never null, empty when no passage is tagged or in an older payload
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
        List<String> ignoreTerms,
        Map<String, Long> passagesByLocale) {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_FAILED = "FAILED";

    /** A missing list or map (an older payload) reads as none; null entries are dropped. */
    public KnowledgeIndexSummary {
        ignoreTerms = ignoreTerms == null ? List.of() : ignoreTerms.stream().filter(Objects::nonNull).toList();
        passagesByLocale = passagesByLocale == null ? Map.of() : Map.copyOf(passagesByLocale.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /** The shape before the locale counts (2.2.0): none known. */
    public KnowledgeIndexSummary(Long id, String name, UUID assistantId, boolean shared, String description,
            long rowCount, long sources, long passages, long pending, String status, LocalDateTime updatedAt,
            boolean legacyName, List<String> ignoreTerms) {
        this(id, name, assistantId, shared, description, rowCount, sources, passages, pending, status, updatedAt,
                legacyName, ignoreTerms, Map.of());
    }

    /**
     * The language most of the index's tagged passages are in, when one holds more than
     * {@code share} of them (0.5: a clear majority); null when no passage is tagged, or none
     * dominates. What a cross-language search compares the question's language with.
     */
    public String dominantLocale(double share) {
        long total = passagesByLocale.values().stream().mapToLong(Long::longValue).sum();
        if (total <= 0) {
            return null;
        }
        return passagesByLocale.entrySet().stream()
                .filter(e -> e.getValue() > share * total)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
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
