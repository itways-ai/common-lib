package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One question/answer row with its embedding, ready to store.
 *
 * <p>
 * Served by journey-service ({@code /api/knowledge-base}), called by
 * conversation-service. One definition, so the two cannot drift.
 *
 * @param chunkText the text that was embedded — the question
 * @param locale    ISO 639-1; null leaves the row untagged
 * @param embeddingModel which model made the vector (e.g. granite-embedding:278m); null when the
 *                       caller did not say, which journey-service treats as unknown
 * @param questionVector the question alone, embedded by the same model as {@code vector} (2.2.0);
 *                       null leaves the row without one until the stale-vector catch-up fills it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeChunk(
        String chunkText,
        String answer,
        String category,
        String notes,
        int rowNumber,
        float[] vector,
        String locale,
        String embeddingModel,
        float[] questionVector) {

    /** Without the question vector (the 2.2.0 shape before it was added). */
    public KnowledgeChunk(String chunkText, String answer, String category, String notes, int rowNumber,
            float[] vector, String locale, String embeddingModel) {
        this(chunkText, answer, category, notes, rowNumber, vector, locale, embeddingModel, null);
    }

    /** Without the model — for callers that predate it. */
    public KnowledgeChunk(String chunkText, String answer, String category, String notes, int rowNumber,
            float[] vector, String locale) {
        this(chunkText, answer, category, notes, rowNumber, vector, locale, null);
    }
}
