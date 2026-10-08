package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One table found on a worksheet (2.3.0): a run of rows and columns with no blank row or blank
 * column inside it, and how it was read.
 *
 * @param title      the title row(s) above the table, when it has any; else null
 * @param mode       {@link #MODE_QA}, {@link #MODE_RECORDS}, {@link #MODE_KEY_VALUE} or {@link #MODE_PROSE}
 * @param range      the cells it covers, title included, as a spreadsheet writes it ({@code A3:D40})
 * @param headerRow  the Excel row (1-based) of its header, or null when it has none
 * @param header     the column names: the header cells, or the column letters without a header
 * @param roles      one per header entry: {@link #ROLE_QUESTION}, {@link #ROLE_ANSWER},
 *                   {@link #ROLE_CATEGORY}, {@link #ROLE_NOTES} or {@link #ROLE_OTHER}
 * @param confidence 0..1, how sure the reader is of the header
 * @param rows       data rows (title and header left out)
 * @param passages   how many passages the block gives
 * @param samples    the first three data rows, cells as shown
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParsedBlock(
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String mode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String range,
        Integer headerRow,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> header,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> roles,
        double confidence,
        int rows,
        int passages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<List<String>> samples) {

    /** A question column and an answer column: one passage per answered row. */
    public static final String MODE_QA = "QA";
    /** A header without a question-and-answer pair: each row read as "Header: value; ...". */
    public static final String MODE_RECORDS = "RECORDS";
    /** Two columns and no header: each row read as "Label: value". */
    public static final String MODE_KEY_VALUE = "KEY_VALUE";
    /** One column: its cells read as paragraphs. */
    public static final String MODE_PROSE = "PROSE";

    public static final String ROLE_QUESTION = "QUESTION";
    public static final String ROLE_ANSWER = "ANSWER";
    public static final String ROLE_CATEGORY = "CATEGORY";
    public static final String ROLE_NOTES = "NOTES";
    public static final String ROLE_OTHER = "OTHER";
}
