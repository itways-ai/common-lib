package com.itways.contracts.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What a store call wrote.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoreChunksResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String indexName,
        int chunksStored,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sourceFile) {
}
