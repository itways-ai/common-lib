package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One question/answer row with its embedding, ready to store.
 *
 * <p>
 * Served by journey-service ({@code /api/knowledge-base}), called by
 * speech-service. One definition, so the two cannot drift.
 *
 * @param chunkText the text that was embedded — the question
 * @param locale    ISO 639-1; null leaves the row untagged
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeChunk(
        String chunkText,
        String answer,
        String category,
        String notes,
        int rowNumber,
        float[] vector,
        String locale) {
}
