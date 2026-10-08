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

    /**
     * One passage's new vector.
     *
     * @param vector         the answer vector, made from the passage's {@code text}
     * @param questionVector the question vector, made from its {@code questionText} by the same
     *                       model (2.2.0); null for a passage that is not a Q&amp;A row
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vector(Long id, float[] vector, float[] questionVector) {

        /** Without the question vector: a passage that is not a Q&amp;A row. */
        public Vector(Long id, float[] vector) {
            this(id, vector, null);
        }
    }
}
