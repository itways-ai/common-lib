package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One row to insert (no id) or update (id set). A null vector updates the
 * row's text and metadata but keeps its embedding.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatchUpsert(
        Long id,
        String question,
        String answer,
        String category,
        String notes,
        float[] vector,
        String locale) {
}
