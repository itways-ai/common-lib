package com.itways.contracts.knowledge;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A question the assistant could not answer well, as speech-service reports it.
 *
 * <p>
 * Served by journey-service ({@code /api/knowledge-base}), called by
 * speech-service. One definition, so the two cannot drift.
 *
 * @param bestScore   similarity of the closest passage found, if any
 * @param bestPassage that passage, for the reviewer's context
 * @param vector      the question's embedding, used to group similar gaps
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GapReport(
        UUID assistantId,
        String question,
        String language,
        String channel,
        Double bestScore,
        String bestPassage,
        float[] vector) {
}
