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
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeSearchRequest(
        String indexName,
        float[] queryVector,
        int limit,
        String locale) {
}
