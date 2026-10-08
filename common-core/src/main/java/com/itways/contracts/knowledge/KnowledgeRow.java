package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A stored row as the editor shows it, without its vector.
 *
 * @param sourceId    the source it came from (2.2.0; null for a row stored before sources existed
 *                    and not yet migrated)
 * @param sourceName  that source's name: a file name, a URL, or "Typed rows" (2.2.0)
 * @param sourceKind  that source's kind, a {@code KnowledgeSourceView.KIND_*} value (2.2.0)
 * @param url         the page a website passage came from (2.2.0)
 * @param locale      ISO 639-1, or null for a language-neutral row (2.2.0)
 * @param enabled     false keeps the row but never serves it (2.2.0). A wrapper, so a payload
 *                    from before 2.2.0 reads as null ("not said"), never as false
 * @param contentHash {@code PassageHashes.sha256Hex(question, answer, locale)} (2.2.0)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeRow(
        Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String question,
        String answer,
        String category,
        String notes,
        int rowNumber,
        Long sourceId,
        String sourceName,
        String sourceKind,
        String url,
        String locale,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean enabled,
        String contentHash) {

    /** The 2.1.0 shape: no source, no locale, enabled, no hash. */
    public KnowledgeRow(Long id, String question, String answer, String category, String notes, int rowNumber) {
        this(id, question, answer, category, notes, rowNumber, null, null, null, null, null, Boolean.TRUE, null);
    }
}
