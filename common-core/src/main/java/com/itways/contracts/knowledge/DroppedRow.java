package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A row that was read but not kept, echoed back so the person who uploaded it can see which
 * and why (2.2.0).
 *
 * @param rowNumber the row's number in the sheet; null for pasted rows without one
 * @param question  the row's question, when it had one
 * @param reason    {@link #NO_ANSWER} or {@link #NO_QUESTION}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DroppedRow(
        Integer rowNumber,
        String question,
        String reason) {

    /** A question without an answer is never served, so it is not stored. */
    public static final String NO_ANSWER = "NO_ANSWER";
    /** A row whose question cell is empty. */
    public static final String NO_QUESTION = "NO_QUESTION";

    /** Without the question. */
    public DroppedRow(Integer rowNumber, String reason) {
        this(rowNumber, null, reason);
    }
}
