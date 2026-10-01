package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * One source of an index — an uploaded file or a crawled page.
 *
 * @param chunks       how many passages it holds
 * @param lastIngested when it was last stored, UTC
 * @deprecated since 2.2.0 journey-service answers {@link KnowledgeSourceView}, which still writes
 *             these three fields for one release; read that instead
 */
@Deprecated(since = "2.2.0", forRemoval = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeSource(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sourceFile,
        long chunks,
        LocalDateTime lastIngested) {
}
