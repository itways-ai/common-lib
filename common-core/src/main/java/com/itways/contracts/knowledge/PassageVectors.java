package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Recomputed passage vectors, all made by {@code embeddingModel}, for
 * journey-service to store ({@code POST /api/journeys/internal/knowledge/embeddings}).
 * The answer to {@link StalePassages}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PassageVectors(String embeddingModel, List<Vector> vectors) {

    /** One passage's new vector. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vector(Long id, float[] vector) {
    }
}
