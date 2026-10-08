package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * What a search did and why (2.2.0): the answer of journey-service's {@code POST /explain} (same
 * request as {@code /search}) and of conversation-service's {@code /query} playground.
 *
 * @param hits        what would be served, in serving order
 * @param dropped     candidates below the gate, best first
 * @param threshold   the threshold applied; null when the request set none (no gate)
 * @param relax       how far below the threshold full-text evidence may admit a hit
 * @param tsConfig    the text-search configuration used for the question ({@code arabic},
 *                    {@code english}, {@code simple}); null when the request had no text
 * @param wouldAnswer the top hit's answer, i.e. what a knowledge step would reply; null when
 *                    nothing passes
 * @param embeddingMs time spent embedding the question; 0 from journey-service, which receives
 *                    the vector ready (conversation-service fills it in, {@link #withEmbeddingMs})
 * @param searchMs    time spent searching, in journey-service
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeSearchExplanation(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<KnowledgeHit> hits,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<KnowledgeHitDrop> dropped,
        Double threshold,
        double relax,
        String tsConfig,
        String wouldAnswer,
        long embeddingMs,
        long searchMs) {

    /** The same explanation with the time spent embedding the question. */
    public KnowledgeSearchExplanation withEmbeddingMs(long embeddingMs) {
        return new KnowledgeSearchExplanation(hits, dropped, threshold, relax, tsConfig, wouldAnswer, embeddingMs,
                searchMs);
    }
}
