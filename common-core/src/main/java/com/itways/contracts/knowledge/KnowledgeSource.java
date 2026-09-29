package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * One source of an index — an uploaded file or a crawled page.
 *
 * @param chunks       how many passages it holds
 * @param lastIngested when it was last stored, UTC
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeSource(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sourceFile,
        long chunks,
        LocalDateTime lastIngested) {
}
