package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One page of an index's rows.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeRowPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String indexName,
        String sourceFile,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<KnowledgeRow> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
