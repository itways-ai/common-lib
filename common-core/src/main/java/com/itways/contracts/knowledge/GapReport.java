package com.itways.contracts.knowledge;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A question the assistant could not answer well, as conversation-service reports it.
 *
 * <p>
 * Served by journey-service ({@code /api/knowledge-base}), called by
 * conversation-service. One definition, so the two cannot drift.
 *
 * @param bestScore   similarity of the closest passage found, if any
 * @param bestPassage that passage, for the reviewer's context
 * @param vector      the question's embedding, used to group similar gaps
 * @param embeddingModel which model made the vector (e.g. granite-embedding:278m); null when the
 *                       caller did not say, which journey-service treats as unknown
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GapReport(
        UUID assistantId,
        String question,
        String language,
        String channel,
        Double bestScore,
        String bestPassage,
        float[] vector,
        String embeddingModel) {

    /** Without the model — for callers that predate it. */
    public GapReport(UUID assistantId, String question, String language, String channel, Double bestScore,
            String bestPassage, float[] vector) {
        this(assistantId, question, language, channel, bestScore, bestPassage, vector, null);
    }
}
