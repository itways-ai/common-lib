package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a store call wrote.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoreChunksResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String indexName,
        int chunksStored,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sourceFile) {
}
