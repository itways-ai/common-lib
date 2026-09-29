package com.itways.contracts.knowledge;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An incremental edit of an index: rows to upsert and rows to delete.
 *
 * <p>
 * Served by journey-service ({@code /api/knowledge-base}), called by
 * conversation-service. One definition, so the two cannot drift.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatchChunksRequest(
        String sourceFile,
        List<PatchUpsert> upserts,
        List<Long> deleteIds) {
}
