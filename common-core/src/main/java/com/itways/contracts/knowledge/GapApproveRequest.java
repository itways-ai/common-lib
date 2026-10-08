package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

/**
 * Answers a group of gaps: stores the question and answer as a row of the index and marks the
 * gaps resolved, in one transaction, idempotently (2.2.0; journey-service
 * {@code POST /gaps/approve}, which replaces {@code /gaps/resolve} after one release).
 *
 * <p>
 * The portal sends it to conversation-service without the vectors; conversation-service embeds
 * the row and forwards it with {@link #withVectors} ({@link #withVector} before the question
 * vector was added).
 *
 * @param gapIds         the group's gaps; only {@code assistantId}'s are touched
 * @param indexName      the index the row goes to (its typed-rows source)
 * @param locale         ISO 639-1 of the row, or null
 * @param vector         the row's answer vector: the question and answer embedded together
 * @param embeddingModel which model made the vector
 * @param questionVector the question alone, embedded by the same model (2.2.0); null leaves the
 *                       row without one until the stale-vector catch-up fills it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GapApproveRequest(
        List<Long> gapIds,
        String indexName,
        String question,
        String answer,
        String locale,
        float[] vector,
        String embeddingModel,
        UUID assistantId,
        float[] questionVector) {

    /** Without the question vector (the 2.2.0 shape before it was added). */
    public GapApproveRequest(List<Long> gapIds, String indexName, String question, String answer, String locale,
            float[] vector, String embeddingModel, UUID assistantId) {
        this(gapIds, indexName, question, answer, locale, vector, embeddingModel, assistantId, null);
    }

    /** The same request with the row's embedding and no question vector. */
    public GapApproveRequest withVector(float[] vector, String embeddingModel) {
        return withVectors(vector, null, embeddingModel);
    }

    /** The same request with the row's embedding and the question's, both made by {@code embeddingModel}. */
    public GapApproveRequest withVectors(float[] vector, float[] questionVector, String embeddingModel) {
        return new GapApproveRequest(gapIds, indexName, question, answer, locale, vector, embeddingModel,
                assistantId, questionVector);
    }
}
