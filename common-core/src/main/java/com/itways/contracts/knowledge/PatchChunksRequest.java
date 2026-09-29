package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

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
