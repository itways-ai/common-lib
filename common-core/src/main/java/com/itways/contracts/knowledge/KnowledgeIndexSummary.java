package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * One knowledge index as lists show it: its name and who it belongs to.
 *
 * <p>
 * Served by journey-service ({@code /api/knowledge-base/indexes}) and passed
 * through by conversation-service's public API. Several indexes may share a name: one
 * per assistant and one shared. References name an index, and the name resolves
 * in the referrer's scope (the assistant's own first, then the shared one).
 *
 * @param assistantId the owning assistant; null when shared
 * @param shared      true when every assistant of the account may use it
 * @param rowCount    how many passages it holds
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeIndexSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        UUID assistantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean shared,
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long rowCount) {
}
