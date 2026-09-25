package com.itways.contracts.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A stored row as the editor shows it, without its vector.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeRow(
        Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String question,
        String answer,
        String category,
        String notes,
        int rowNumber) {
}
