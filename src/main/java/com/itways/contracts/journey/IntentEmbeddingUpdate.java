package com.itways.contracts.journey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A freshly computed intent vector for one journey version.
 *
 * <p>
 * assistant-service computes it (it owns the embedding model) and posts it to
 * journey-service ({@code POST /api/journeys/intent-catalog/embeddings}), which
 * stores it. Shared so both sides agree on the shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntentEmbeddingUpdate(
		Long versionId,
		float[] vector) {
}
