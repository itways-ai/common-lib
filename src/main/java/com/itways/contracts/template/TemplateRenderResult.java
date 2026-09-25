package com.itways.contracts.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Shared by template-service, which renders, and speech-service, whose
 * TEMPLATE_RENDER step asks it to ({@code POST /api/templates/{id}/render}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "The result of a render. A template that fails to render is reported here " +
                      "with 'error' set and HTTP 200 — it is a problem with the template, not with " +
                      "the request or the service, and both callers need to tell those apart.")
public class TemplateRenderResult {

    @Schema(
        description = "The rendered output. Null when 'error' is set.",
        example = "<h1>Hello, Sarah!</h1>"
    )
    private String output;

    @Schema(
        description = "The template's content type, so the caller can choose a renderer.",
        example = "html"
    )
    private String contentType;

    @Schema(
        description = "Variables the template referenced that the supplied data did not provide. " +
                      "Populated only when the template allows failure; otherwise their presence " +
                      "is reported as an error instead.",
        example = "[\"lastName\"]"
    )
    private List<String> unresolved;

    @Schema(
        description = "Everything the template expects to be supplied. Returned by /preview so the " +
                      "editor can list a template's variables before it has been saved; omitted by " +
                      "/{id}/render, whose caller already knows what it bound."
    )
    private List<TemplateVariable> variables;

    @Schema(
        description = "Why the render failed, or null on success.",
        example = "Unresolved template variables: lastName"
    )
    private String error;

    @Schema(description = "Line the failure was reported at, when known.", example = "3")
    private Integer errorLine;

    @Schema(description = "Column the failure was reported at, when known.", example = "14")
    private Integer errorColumn;
}
