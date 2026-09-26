package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A vector search over one index.
 *
 * <p>
 * Served by journey-service ({@code /api/knowledge-base}), called by
 * speech-service. One definition, so the two cannot drift.
 *
 * @param limit  at most this many hits; 0 or less means the default
 * @param locale the conversation language; null searches every locale
 * @param embeddingModel which model made the vector (e.g. granite-embedding:278m); null when the
 *                       caller did not say, which journey-service treats as unknown
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeSearchRequest(
        String indexName,
        float[] queryVector,
        int limit,
        String locale,
        String embeddingModel) {

    /** Without the model — every chunk is a candidate, whichever model embedded it. */
    public KnowledgeSearchRequest(String indexName, float[] queryVector, int limit, String locale) {
        this(indexName, queryVector, limit, locale, null);
    }
}
