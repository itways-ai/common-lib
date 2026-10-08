package com.itways.contracts.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A value the template expects to be supplied at render time. " +
                      "One entry per root name — a template using ${user.name} and ${user.email} " +
                      "reports a single 'user' variable with both paths listed.")
public class TemplateVariable {

    @Schema(
        description = "The root name to bind. This is the key the render model must contain.",
        example = "user"
    )
    private String name;

    @Schema(
        description = "How the template uses the value. 'scalar' is written directly, " +
                      "'hash' has properties read from it, 'list' is iterated with <#list>.",
        example = "hash",
        allowableValues = {"scalar", "hash", "list"}
    )
    private String kind;

    @Schema(
        description = "Every dotted path observed under this name. Empty for scalars. " +
                      "Useful for showing the builder what shape the value needs.",
        example = "[\"user.name\", \"user.email\"]"
    )
    private List<String> paths;

    @Schema(
        description = "True when the template copes with the value being absent: every use has a " +
                      "default (x!, x!\"...\", (x.y)!), is an existence test (x??, x?has_content, " +
                      "x?default(...)), or sits inside <#if x??> / <#if x?has_content>. " +
                      "One unguarded use makes it required. Missing in older answers means required.",
        example = "false"
    )
    private boolean optional;

    /** A required variable: the shape every caller used before {@code optional} existed. */
    public TemplateVariable(String name, String kind, List<String> paths) {
        this(name, kind, paths, false);
    }
}
