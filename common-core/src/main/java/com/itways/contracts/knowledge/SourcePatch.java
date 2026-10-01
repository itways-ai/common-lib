package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An ingestion worker's update of the source it holds (2.2.0; journey-service
 * {@code PATCH /ingestion/sources/{id}}). Every field is optional: null leaves it unchanged.
 *
 * @param status       {@code KnowledgeSourceView.STATUS_READY} or {@code STATUS_FAILED}; READY
 *                     recounts the source's passages
 * @param progress     0..100
 * @param errorCode    with FAILED: our code ({@code EMBEDDING_UNAVAILABLE}, {@code SOURCE_UNREACHABLE},
 *                     {@code SOURCE_TOO_LARGE}, {@code SOURCE_UNREADABLE}, {@code INTERRUPTED}, or
 *                     journey-service's own)
 * @param errorMessage with FAILED: a sentence for the person, never a library's text
 * @param contentHash  with READY: {@code PassageHashes.sourceHash} of the source's passages
 * @param leaseSeconds extends the lease by this much from now (a heartbeat)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourcePatch(
        String status,
        Integer progress,
        String errorCode,
        String errorMessage,
        String contentHash,
        Integer leaseSeconds) {

    /** Still working: the progress so far, and the lease extended. */
    public static SourcePatch heartbeat(int progress, int leaseSeconds) {
        return new SourcePatch(null, progress, null, null, null, leaseSeconds);
    }

    /** Done: every passage has its vector. */
    public static SourcePatch ready(String contentHash) {
        return new SourcePatch(KnowledgeSourceView.STATUS_READY, 100, null, null, contentHash, null);
    }

    /** Given up: why, in our words. */
    public static SourcePatch failed(String errorCode, String errorMessage) {
        return new SourcePatch(KnowledgeSourceView.STATUS_FAILED, null, errorCode, errorMessage, null, null);
    }
}
