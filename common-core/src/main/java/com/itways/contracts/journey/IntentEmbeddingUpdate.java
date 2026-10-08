package com.itways.contracts.journey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A freshly computed intent vector for one journey version.
 *
 * <p>
 * conversation-service computes it (it owns the embedding model) and posts it to
 * journey-service ({@code POST /api/journeys/internal/intent-catalog/embeddings}), which
 * stores it. Shared so both sides agree on the shape.
 * @param embeddingModel which model made the vector (e.g. granite-embedding:278m); null when the
 *                       caller did not say, which journey-service treats as unknown
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntentEmbeddingUpdate(
        Long versionId,
        float[] vector,
        String embeddingModel) {

    /** Without the model — for callers that predate it; journey-service stores the vector as of unknown origin. */
    public IntentEmbeddingUpdate(Long versionId, float[] vector) {
        this(versionId, vector, null);
    }
}
