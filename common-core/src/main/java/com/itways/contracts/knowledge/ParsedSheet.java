package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A spreadsheet read for review before it is added (2.2.0): the rows that will be stored and
 * the ones that will not.
 *
 * <p>
 * Since 2.3.0 a sheet needs no fixed structure: every worksheet is read, cut into blocks at
 * blank rows and columns, and each block is either question-and-answer rows ({@code QA}: its
 * rows are in {@code rows}) or free records read as text ({@code RECORDS}, {@code KEY_VALUE},
 * {@code PROSE}). The structure found is in {@code sheets}; a payload from a 2.2.0 service has
 * only the first three fields, and the new ones read as {@code null}, {@code 0} and
 * {@code false}.
 *
 * @param rows        the question-and-answer rows to store (the {@code QA} blocks' rows)
 * @param dropped     how many rows were left out ({@code droppedRows.size()} unless the list was
 *                    cut short)
 * @param droppedRows which rows were left out, and why
 * @param kind        (2.3.0) {@link KnowledgeSourceView#KIND_SHEET} when at least one block is
 *                    question-and-answer rows, else {@link KnowledgeSourceView#KIND_DOCUMENT}
 * @param passages    (2.3.0) how many passages the whole workbook gives, every block counted
 * @param truncated   (2.3.0) whether reading stopped at a limit (passages, sheets, columns, cell
 *                    length)
 * @param sheets      (2.3.0) every worksheet in workbook order, hidden ones included (with no
 *                    blocks)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParsedSheet(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ParsedRow> rows,
        int dropped,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DroppedRow> droppedRows,
        String kind,
        int passages,
        boolean truncated,
        List<ParsedWorksheet> sheets) {

    /** The 2.2.0 shape: rows and dropped rows only, no structure. */
    public ParsedSheet(List<ParsedRow> rows, int dropped, List<DroppedRow> droppedRows) {
        this(rows, dropped, droppedRows, null, 0, false, null);
    }
}
