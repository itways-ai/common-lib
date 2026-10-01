package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A passage waiting for its embedding (2.2.0; journey-service
 * {@code GET /ingestion/sources/{id}/passages?pending=true}): its id and the exact text to
 * embed. The vectors go back as {@link PassageVectors}.
 *
 * @param text         the text the answer vector is made from (question and answer of a Q&amp;A
 *                     row, the passage text otherwise)
 * @param questionText the question alone, as stored, for the question vector (2.2.0); non-null
 *                     only for a Q&amp;A row (a sheet or typed row with an answer), null for a
 *                     document or web passage, which gets no question vector
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PendingPassage(
        long id,
        String text,
        String questionText) {

    /** Without the question: a passage that gets no question vector. */
    public PendingPassage(long id, String text) {
        this(id, text, null);
    }
}
