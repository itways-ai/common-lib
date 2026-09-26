package com.itways.contracts.journey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A journey whose steps render a given template. Returned by journey-service
 * ({@code GET /api/journeys/internal/template-usage/{templateId}}) so template-service can
 * refuse to delete a template that a journey still depends on.
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
}
