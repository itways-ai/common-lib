package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One batch of an account's passages whose vector another embedding model
 * made, oldest first, with the exact text to embed again.
 *
 * <p>
 * Served by journey-service ({@code /api/journeys/internal/knowledge/embeddings/stale}),
 * called by conversation-service, which owns the model. One definition, so the two
 * cannot drift.
 *
 * <p>
 * Since 2.2.0 a Q&amp;A row whose question vector is missing is stale too, even when its answer
 * vector is current: it comes back with its {@code questionText} so the catch-up can fill it.
 *
 * @param remaining how many passages of the account are stale, this batch included
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StalePassages(List<Passage> passages, long remaining) {

    /**
     * A passage to embed again: its id and the text its vector is made from.
     *
     * @param questionText the question alone, for the question vector (2.2.0); non-null only for
     *                     a Q&amp;A row
     * @param ignoreTerms  the ignore terms of the passage's index (2.2.0), to strip before
     *                     embedding as the ingestion does; null or empty strips nothing
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Passage(long id, String text, String questionText, List<String> ignoreTerms) {

        /** Without the question or the index's terms: the shape before they were added. */
        public Passage(long id, String text) {
            this(id, text, null, null);
        }
    }
}
