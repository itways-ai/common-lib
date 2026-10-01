package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Creates an empty index (2.2.0). The name must pass {@link KnowledgeIndexName} after
 * {@link KnowledgeIndexName#normalize normalising} (400 {@code INDEX_NAME_INVALID}) and be free
 * in its scope, case-insensitively (409 {@code INDEX_NAME_TAKEN}).
 *
 * @param description display text; the name is an identifier
 * @param shared      true for a shared index (the portal's body; journey-service reads the
 *                    scope from the request's scope parameters, as its other routes do)
 * @param assistantId the owning assistant (the portal's body, as {@code shared})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateIndexRequest(
        String name,
        String description,
        Boolean shared,
        UUID assistantId) {

    /** Name and description only: the scope comes from the request's scope parameters. */
    public CreateIndexRequest(String name, String description) {
        this(name, description, null, null);
    }
}
