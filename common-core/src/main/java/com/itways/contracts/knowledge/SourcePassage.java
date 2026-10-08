package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.itways.common.text.PassageHashes;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One passage of a source, read but not yet embedded (2.2.0): a sheet or typed row (question
 * and answer) or a piece of a document or web page (text only).
 *
 * @param text        what is embedded and searched: the question, or the passage text
 * @param answer      the answer; null for a document or web passage
 * @param rowNumber   the sheet row, when it came from a sheet
 * @param locale      ISO 639-1, or null for a language-neutral passage
 * @param contentHash {@code PassageHashes.sha256Hex(text, answer, locale)}; the passage's identity
 *                    within its source, so a re-upload keeps unchanged passages and their vectors
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourcePassage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String text,
        String answer,
        String category,
        String notes,
        Integer rowNumber,
        String locale,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String contentHash) {

    /** A passage with its {@code contentHash} computed from {@code text}, {@code answer} and {@code locale}. */
    public static SourcePassage of(String text, String answer, String category, String notes, Integer rowNumber,
            String locale) {
        return new SourcePassage(text, answer, category, notes, rowNumber, locale,
                PassageHashes.sha256Hex(text, answer, locale));
    }
}
