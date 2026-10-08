package com.itways.contracts.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shared by template-service, which renders, and conversation-service, whose
 * TEMPLATE_RENDER step asks it to ({@code POST /api/templates/{id}/render}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@Schema(description = "Renders a stored template. Called by the journey engine when a " +
                      "TEMPLATE_RENDER step executes, with the step's bindings already resolved " +
                      "to values.")
public class TemplateRenderRequest {

    @Schema(
        description = "The values to render against, keyed by the names reported by " +
                      "GET /api/templates/{id}/variables.",
        example = "{\"firstName\": \"Sarah\", \"orderCount\": 3}"
    )
    private Map<String, Object> data;

    @Schema(
        description = "The template version to render. A published journey sends the version it was " +
                      "published with, so later edits reach it only when it is published again. " +
                      "Omitted: the template's current version.",
        example = "3"
    )
    private Integer version;

    public TemplateRenderRequest(Map<String, Object> data) {
        this.data = data;
    }

    public TemplateRenderRequest(Map<String, Object> data, Integer version) {
        this.data = data;
        this.version = version;
    }
}
