package com.itways.contracts.journey;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A journey whose steps render a given template. Returned by journey-service
 * ({@code GET /api/journeys/internal/template-usage/{templateId}}) so template-service can
 * refuse to delete a template that a journey still depends on, and refuse to move it to an
 * assistant whose templates those journeys may not use.
 *
 * <p>
 * The draft and the live version can sit in different scopes: a journey moved to another
 * assistant keeps its live version where it was published until it is published again. So
 * the draft's scope is {@code assistantId} and the live version's is
 * {@code publishedAssistantId}; each matters only when {@code draft} or {@code published}
 * says that side renders the template.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A journey that renders the template in its draft or in its live published version.")
public class TemplateUsage {

    @Schema(description = "The journey's id.", example = "42")
    private Long journeyId;

    @Schema(description = "The journey's name.", example = "Order shipped")
    private String name;

    @Schema(description = "The live published version renders the template.")
    private boolean published;

    @Schema(description = "The draft renders the template.")
    private boolean draft;

    @Schema(description = "The assistant the journey belongs to; null when it is shared by every assistant.")
    private UUID assistantId;

    @Schema(description = "The journey is shared by every assistant (assistantId is null).")
    private boolean shared;

    @Schema(description = "The assistant the live version was published under; null when that version is "
            + "shared, or when the journey has no live version that renders the template.")
    private UUID publishedAssistantId;
}
