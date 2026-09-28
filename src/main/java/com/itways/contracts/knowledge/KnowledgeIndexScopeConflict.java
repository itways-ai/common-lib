package com.itways.contracts.knowledge;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Why an index was not moved: the data of the 409 {@code INDEX_SCOPE_IN_USE}.
 * Each entry reads the index by name today and would no longer reach it from
 * the new scope (a shared journey cannot read an assistant's index; another
 * assistant cannot read this one's).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeIndexScopeConflict(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String indexName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<KnowledgeIndexUse> blockers) {
}
