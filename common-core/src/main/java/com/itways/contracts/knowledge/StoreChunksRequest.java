package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Pre-embedded rows for one index, from one source file or page.
 *
 * <p>
 * Served by journey-service ({@code /api/knowledge-base}), called by
 * conversation-service. One definition, so the two cannot drift.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoreChunksRequest(
        String indexName,
        String sourceFile,
        List<KnowledgeChunk> chunks) {
}
