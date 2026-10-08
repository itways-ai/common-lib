package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One worksheet of an uploaded spreadsheet as it was read (2.3.0), for the review before it is
 * added: a CSV is one worksheet named after the file.
 *
 * @param name    the worksheet's name
 * @param index   its position in the workbook, 0-based
 * @param hidden  whether the workbook hides it; a hidden sheet is skipped and has no blocks
 * @param rows    how many rows have content
 * @param columns how many columns are read (at most the column limit)
 * @param blocks  the tables found on it, top to bottom
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParsedWorksheet(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        int index,
        boolean hidden,
        int rows,
        int columns,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ParsedBlock> blocks) {
}
