package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row read from an uploaded spreadsheet, before embedding.
 *
 * @param rowNumber the row's number in the sheet, so errors can point at it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParsedRow(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String question,
        String answer,
        String category,
        String notes,
        int rowNumber) {
}
