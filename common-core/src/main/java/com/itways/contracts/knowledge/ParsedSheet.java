package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A spreadsheet read for review before it is added (2.2.0): the rows that will be stored and
 * the ones that will not.
 *
 * @param rows        the rows to store
 * @param dropped     how many rows were left out ({@code droppedRows.size()} unless the list was
 *                    cut short)
 * @param droppedRows which rows were left out, and why
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParsedSheet(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ParsedRow> rows,
        int dropped,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DroppedRow> droppedRows) {
}
