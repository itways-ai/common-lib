package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One row to insert (no id) or update (id set). A null vector updates the
 * row's text and metadata but keeps its embedding.
 * @param embeddingModel which model made the vector (e.g. granite-embedding:278m); null when the
 *                       caller did not say, which journey-service treats as unknown
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatchUpsert(
        Long id,
        String question,
        String answer,
        String category,
        String notes,
        float[] vector,
        String locale,
        String embeddingModel) {

    /** Without the model — for callers that predate it. */
    public PatchUpsert(Long id, String question, String answer, String category, String notes, float[] vector,
            String locale) {
        this(id, question, answer, category, notes, vector, locale, null);
    }
}
