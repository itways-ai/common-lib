package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A candidate the gate did not serve, listed by {@link KnowledgeSearchExplanation} so the
 * playground can show what almost answered (2.2.0).
 *
 * @param question the candidate's question (or passage text), so the playground can show it
 * @param reason   {@link KnowledgeHit#REASON_BELOW_THRESHOLD}, or journey-service's
 *                 {@code BELOW_LOCALE_THRESHOLD}
 * @param matchedTerms as {@link KnowledgeHit#matchedTerms()}: why full-text evidence did or did not
 *                     corroborate it
 * @param termCoverage as {@link KnowledgeHit#termCoverage()}
 * @param questionScore as {@link KnowledgeHit#questionScore()} (2.2.0)
 * @param vectorMatch   as {@link KnowledgeHit#vectorMatch()} (2.2.0)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeHitDrop(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        String question,
        Double vectorScore,
        Double lexicalScore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        Integer matchedTerms,
        Double termCoverage,
        Double questionScore,
        String vectorMatch) {

    /** Without the question vector's score and match. */
    public KnowledgeHitDrop(Long id, String question, Double vectorScore, Double lexicalScore, String reason,
            Integer matchedTerms, Double termCoverage) {
        this(id, question, vectorScore, lexicalScore, reason, matchedTerms, termCoverage, null, null);
    }

    /** Without the term counts. */
    public KnowledgeHitDrop(Long id, String question, Double vectorScore, Double lexicalScore, String reason) {
        this(id, question, vectorScore, lexicalScore, reason, null, null);
    }

    /** Without the question. */
    public KnowledgeHitDrop(Long id, Double vectorScore, Double lexicalScore, String reason) {
        this(id, null, vectorScore, lexicalScore, reason);
    }
}
