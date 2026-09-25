package com.itways.contracts.knowledge;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Pre-embedded rows for one index, from one source file or page.
 *
 * <p>
 * Served by journey-service ({@code /api/knowledge-base}), called by
 * speech-service. One definition, so the two cannot drift.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoreChunksRequest(
        String indexName,
        String sourceFile,
        List<KnowledgeChunk> chunks) {
}
