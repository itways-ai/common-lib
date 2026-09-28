package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Something that reads an index by name: a journey's knowledge step (in its
 * draft, or in the version conversations run) or an assistant's list of
 * indexes.
 *
 * @param kind {@link #JOURNEY_DRAFT}, {@link #JOURNEY_PUBLISHED} or {@link #ASSISTANT}
 * @param id   the journey's id, or the assistant's
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeIndexUse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {

    public static final String JOURNEY_DRAFT = "JOURNEY_DRAFT";
    public static final String JOURNEY_PUBLISHED = "JOURNEY_PUBLISHED";
    public static final String ASSISTANT = "ASSISTANT";
}
