package com.itways.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Marks every primitive field of a DTO as required in the API description.
 *
 * <p>
 * A primitive can't be null, so Jackson always writes it: an {@code int count}
 * is on the wire whether or not anyone annotated it. Springdoc only marks what
 * is annotated, so without this every counter, flag and total came out optional
 * and generated clients had to null-check values that are never missing.
 *
 * <p>
 * Reference types stay optional unless their class says otherwise with
 * {@code @Schema(requiredMode = REQUIRED)} — there, only the author knows.
 *
 * <p>
 * Responses only. A schema a request body can reach keeps its primitives
 * optional: the server accepts them missing (they default), and requiring them
 * would make clients send values the server ignores or computes itself, such as
 * a category's journey count.
 */
public class PrimitiveFieldsRequired implements ModelConverter, OpenApiCustomizer {

    /** Schema name → the properties this converter made required. */
    private final Map<String, Set<String>> added = new ConcurrentHashMap<>();

    @Override
    public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema<?> schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        if (schema == null || type.getType() == null) {
            return schema;
        }
        JavaType javaType = Json.mapper().constructType(type.getType());
        Class<?> raw = javaType.getRawClass();
        if (raw.isPrimitive() || raw.isArray() || raw.isEnum() || raw.getName().startsWith("java.")
                || javaType.isContainerType()) {
            return schema;
        }
        Schema<?> target = schema;
        String schemaName = schema.getName();
        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            schemaName = ref.substring(ref.lastIndexOf('/') + 1);
            target = context.getDefinedModels().get(schemaName);
        }
        if (target == null || target.getProperties() == null) {
            return schema;
        }
        for (Class<?> c = raw; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!field.getType().isPrimitive() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String name = jsonName(field);
                boolean present = target.getProperties().containsKey(name);
                boolean already = target.getRequired() != null && target.getRequired().contains(name);
                if (present && !already) {
                    target.addRequiredItem(name);
                    if (schemaName != null) {
                        added.computeIfAbsent(schemaName, k -> ConcurrentHashMap.newKeySet()).add(name);
                    }
                }
            }
        }
        return schema;
    }

    /** Undoes the rule for every schema a request can send. */
    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null
                || openApi.getPaths() == null) {
            return;
        }
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        Deque<Schema<?>> pending = new ArrayDeque<>();
        openApi.getPaths().values().forEach(item -> item.readOperations().forEach(op -> {
            if (op.getRequestBody() != null) {
                addContent(pending, op.getRequestBody().getContent());
            }
            if (op.getParameters() != null) {
                for (Parameter p : op.getParameters()) {
                    if (p.getSchema() != null) {
                        pending.add(p.getSchema());
                    }
                }
            }
        }));
        Set<String> requestSchemas = new HashSet<>();
        while (!pending.isEmpty()) {
            Schema<?> s = pending.pop();
            if (s.get$ref() != null) {
                String name = s.get$ref().substring(s.get$ref().lastIndexOf('/') + 1);
                if (requestSchemas.add(name) && schemas.get(name) != null) {
                    pending.add(schemas.get(name));
                }
                continue;
            }
            if (s.getProperties() != null) {
                s.getProperties().values().forEach(p -> pending.add((Schema<?>) p));
            }
            if (s.getItems() != null) {
                pending.add(s.getItems());
            }
            if (s.getAdditionalProperties() instanceof Schema<?> extra) {
                pending.add(extra);
            }
            for (List<Schema> list : List.of(nullToEmpty(s.getAllOf()), nullToEmpty(s.getAnyOf()),
                    nullToEmpty(s.getOneOf()))) {
                list.forEach(x -> pending.add((Schema<?>) x));
            }
        }
        for (String name : requestSchemas) {
            Schema<?> schema = schemas.get(name);
            Set<String> mine = added.get(name);
            if (schema == null || mine == null || schema.getRequired() == null) {
                continue;
            }
            schema.getRequired().removeAll(mine);
            if (schema.getRequired().isEmpty()) {
                schema.setRequired(null);
            }
        }
    }

    private static void addContent(Deque<Schema<?>> pending, Content content) {
        if (content != null) {
            for (MediaType media : content.values()) {
                if (media.getSchema() != null) {
                    pending.add(media.getSchema());
                }
            }
        }
    }

    private static List<Schema> nullToEmpty(List<Schema> list) {
        return list == null ? List.of() : list;
    }

    private static String jsonName(Field field) {
        JsonProperty property = field.getAnnotation(JsonProperty.class);
        return property != null && !property.value().isEmpty() ? property.value() : field.getName();
    }
}
