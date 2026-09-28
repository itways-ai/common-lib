package com.itways.contracts.knowledge;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Moves an index to another scope: {@code shared=true} makes it shared, an
 * {@code assistantId} gives it to that assistant, neither leaves it where it is.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeIndexScopeChange(
        UUID assistantId,
        Boolean shared) {
}
