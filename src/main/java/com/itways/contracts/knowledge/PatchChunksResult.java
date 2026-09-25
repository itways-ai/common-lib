package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What a patch changed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatchChunksResult(
        String indexName,
        int inserted,
        int updated,
        int deleted,
        String sourceFile) {
}
