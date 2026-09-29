package com.itways.contracts.journey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A journey version whose intent text changed after it was last embedded.
 *
 * <p>
 * journey-service lists them ({@code GET /api/journeys/intent-catalog/stale});
 * assistant-service embeds the text and sends back an
 * {@link IntentEmbeddingUpdate}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StaleIntentEmbedding(
		Long versionId,
		String embeddingText) {
}
