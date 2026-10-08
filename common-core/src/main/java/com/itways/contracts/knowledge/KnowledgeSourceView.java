package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One source of an index and where its ingestion stands (2.2.0): a sheet, a document, a web
 * page or the index's typed rows. Replaces {@link KnowledgeSource} as the answer of
 * journey-service's {@code GET /{index}/sources}; for one release it also carries that record's
 * fields ({@code sourceFile}, {@code chunks}, {@code lastIngested}), so a reader still on 2.1.0
 * keeps working.
 *
 * <p>
 * Lifecycle: {@link #STATUS_QUEUED} (passages stored, waiting for a worker) →
 * {@link #STATUS_PROCESSING} (a worker holds it) → {@link #STATUS_READY}, or
 * {@link #STATUS_FAILED} with {@code errorCode} and {@code errorMessage}.
 *
 * @param kind         {@link #KIND_SHEET}, {@link #KIND_DOCUMENT}, {@link #KIND_WEBSITE} or {@link #KIND_QA}
 * @param name         the file name, the URL, or "Typed rows"; unique within the index
 * @param url          the page, for a website source
 * @param errorCode    why it failed; null unless FAILED
 * @param errorMessage the same in words, for the person; null unless FAILED
 * @param passages     how many passages it holds
 * @param pending      how many of them still wait for their embedding
 * @param progress     0..100
 * @param contentHash  {@code PassageHashes.sourceHash} of its passages, once READY
 * @param attempts     how many times a worker has claimed it
 * @param ingestedAt   when it last became READY, UTC
 * @param dropped      upload notes, set by conversation-service's answer to an upload only (null
 *                     from journey-service): how many rows were left out
 * @param droppedRows  upload notes: which rows were left out, and why
 * @param truncated    upload notes: true when the reader stopped at the passage cap
 * @param ignoreTerms  the index's ignore terms ({@link KnowledgeIndexSummary#ignoreTerms}), set in
 *                     journey-service's answer to {@code POST /ingestion/claim} only (null
 *                     elsewhere), so the worker leaves them out of the text it embeds
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeSourceView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        String url,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        String errorCode,
        String errorMessage,
        int passages,
        int pending,
        int progress,
        String contentHash,
        int attempts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime ingestedAt,
        Integer dropped,
        List<DroppedRow> droppedRows,
        Boolean truncated,
        List<String> ignoreTerms) {

    /** An uploaded spreadsheet (.xlsx, .xls, .csv), one passage per row. */
    public static final String KIND_SHEET = "SHEET";
    /** An uploaded document (.pdf, .docx, .md, .txt, .html), split into passages. */
    public static final String KIND_DOCUMENT = "DOCUMENT";
    /** A web page, fetched and re-read by the worker. */
    public static final String KIND_WEBSITE = "WEBSITE";
    /** Questions and answers typed or pasted in the portal, or approved from gaps. */
    public static final String KIND_QA = "QA";

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    /** As journey-service answers: without the upload notes. */
    public KnowledgeSourceView(Long id, String kind, String name, String url, String status, String errorCode,
            String errorMessage, int passages, int pending, int progress, String contentHash, int attempts,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime ingestedAt) {
        this(id, kind, name, url, status, errorCode, errorMessage, passages, pending, progress, contentHash, attempts,
                createdAt, updatedAt, ingestedAt, null, null, null, null);
    }

    /** The shape before ignore terms: with upload notes, no terms. */
    public KnowledgeSourceView(Long id, String kind, String name, String url, String status, String errorCode,
            String errorMessage, int passages, int pending, int progress, String contentHash, int attempts,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime ingestedAt, Integer dropped,
            List<DroppedRow> droppedRows, Boolean truncated) {
        this(id, kind, name, url, status, errorCode, errorMessage, passages, pending, progress, contentHash, attempts,
                createdAt, updatedAt, ingestedAt, dropped, droppedRows, truncated, null);
    }

    /** The same view with the notes of the upload that created it (conversation-service). */
    public KnowledgeSourceView withUploadNotes(Integer dropped, List<DroppedRow> droppedRows, Boolean truncated) {
        return new KnowledgeSourceView(id, kind, name, url, status, errorCode, errorMessage, passages, pending,
                progress, contentHash, attempts, createdAt, updatedAt, ingestedAt, dropped, droppedRows, truncated,
                ignoreTerms);
    }

    /** The same view with its index's ignore terms (journey-service's claim answer). */
    public KnowledgeSourceView withIgnoreTerms(List<String> terms) {
        return new KnowledgeSourceView(id, kind, name, url, status, errorCode, errorMessage, passages, pending,
                progress, contentHash, attempts, createdAt, updatedAt, ingestedAt, dropped, droppedRows, truncated,
                terms == null ? null : List.copyOf(terms));
    }

    /** {@code KnowledgeSource.sourceFile} (2.1.0): the name. Written for one release, never read. */
    @Deprecated(since = "2.2.0", forRemoval = true)
    @JsonProperty(value = "sourceFile", access = JsonProperty.Access.READ_ONLY)
    public String sourceFile() {
        return name;
    }

    /** {@code KnowledgeSource.chunks} (2.1.0): the passages. Written for one release, never read. */
    @Deprecated(since = "2.2.0", forRemoval = true)
    @JsonProperty(value = "chunks", access = JsonProperty.Access.READ_ONLY)
    public long chunks() {
        return passages;
    }

    /** {@code KnowledgeSource.lastIngested} (2.1.0): when it was last READY, else last changed. */
    @Deprecated(since = "2.2.0", forRemoval = true)
    @JsonProperty(value = "lastIngested", access = JsonProperty.Access.READ_ONLY)
    public LocalDateTime lastIngested() {
        return ingestedAt != null ? ingestedAt : updatedAt;
    }
}
