package com.itways.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Describes a Java {@code Object} as "any value" instead of "an object".
 *
 * <p>
 * Springdoc writes {@code Object} — the value type of every
 * {@code Map<String, Object>}, and the {@code data} of an
 * {@code ApiResponse<Void>} — as {@code {"type": "object"}} with no properties.
 * Generated clients read that literally, as an object that can hold nothing
 * ({@code Record<string, never>} in TypeScript), so channel settings, step views
 * and journey variables all came out unusable. An empty schema says what the
 * Java type means: any JSON value.
 *
 * <p>
 * Done on the finished document because swagger-core maps {@code Object}
 * before any model converter runs.
 */
public class AnyValueSchemas implements OpenApiCustomizer {

    private final Set<Schema<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());

    @Override
    public void customise(OpenAPI openApi) {
        seen.clear();
        if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
            openApi.getComponents().getSchemas().values().forEach(this::visitChildren);
        }
        if (openApi.getPaths() != null) {
            openApi.getPaths().values().forEach(item -> item.readOperations().forEach(this::visit));
        }
    }

    private void visit(Operation operation) {
        if (operation.getParameters() != null) {
            for (Parameter p : operation.getParameters()) {
                visitSchema(p.getSchema());
            }
        }
        if (operation.getRequestBody() != null) {
            visit(operation.getRequestBody().getContent());
        }
        if (operation.getResponses() != null) {
            operation.getResponses().values().forEach(r -> visit(r.getContent()));
        }
    }

    private void visit(Content content) {
        if (content != null) {
            for (MediaType media : content.values()) {
                visitSchema(media.getSchema());
            }
        }
    }

    /** A nested schema: loosened if it is a bare object, otherwise walked. */
    private void visitSchema(Schema<?> schema) {
        if (schema == null || !seen.add(schema)) {
            return;
        }
        if (isBareObject(schema)) {
            schema.setType(null);
            return;
        }
        visitChildren(schema);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void visitChildren(Schema<?> schema) {
        if (schema.getProperties() != null) {
            for (Map.Entry<String, Schema> e : ((Map<String, Schema>) (Map) schema.getProperties()).entrySet()) {
                visitSchema(e.getValue());
            }
        }
        if (schema.getAdditionalProperties() instanceof Schema<?> extra) {
            visitSchema(extra);
        }
        visitSchema(schema.getItems());
        for (var list : new java.util.List[] { schema.getAllOf(), schema.getAnyOf(), schema.getOneOf() }) {
            if (list != null) {
                list.forEach(s -> visitSchema((Schema<?>) s));
            }
        }
    }

    private static boolean isBareObject(Schema<?> s) {
        return "object".equals(s.getType()) && s.get$ref() == null && s.getProperties() == null
                && s.getAdditionalProperties() == null && s.getAllOf() == null && s.getAnyOf() == null
                && s.getOneOf() == null;
    }
}
