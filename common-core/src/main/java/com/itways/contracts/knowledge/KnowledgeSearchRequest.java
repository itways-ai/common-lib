package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * A search over one index: by vector, and since 2.2.0 also by the question's text (hybrid
 * search) with a similarity gate and diversity.
 *
 * <p>
 * Served by journey-service ({@code /api/journeys/internal/knowledge-base/search} and
 * {@code /explain}), called by conversation-service. One definition, so the two cannot drift.
 *
 * @param limit  at most this many hits; 0 or less means the default
 * @param locale the conversation language. Since 2.3.0 a ranking preference, not a filter: every
 *               passage of the index is searched whatever its language, and a passage tagged with
 *               this language ranks a little higher ({@code locale-boost}), so a bilingual index
 *               answers in the conversation's language. Before 2.3.0 it filtered the search to
 *               passages of this language and untagged ones, which hid an English index from an
 *               Arabic question. Null: no preference
 * @param embeddingModel which model made the vector (e.g. granite-embedding:278m); null when the
 *                       caller did not say, which journey-service treats as unknown
 * @param assistantId the assistant the search runs for. {@code indexName} resolves in its scope: its
 *                    own index of that name, else the shared one. Null searches shared indexes only,
 *                    so a conversation without an assistant never reads any assistant's knowledge
 * @param query     the question as asked (2.2.0): adds the full-text leg to the vector search and
 *                  gives the gate its lexical evidence; null searches by vector only
 * @param threshold the cosine similarity a hit needs (2.2.0; the step's setting, 0.30..0.95). A hit
 *                  with full-text evidence may be admitted slightly below it (journey-service's
 *                  {@code lexical-relax}); null applies no gate, as before 2.2.0
 * @param diversity 0..1 (2.2.0): how much the hits are spread over different passages (MMR); 0
 *                  keeps the relevance order; null leaves it to journey-service
 * @param recall    true for a recall search (2.2.0): the caller gathers candidates for a model to
 *                  judge (a COMPOSE step), so {@code threshold} is a recall floor and is applied as
 *                  sent for every language; journey-service adds no language offset to it (its
 *                  Arabic vector offset). Null or false: a serving search, gated as usual
 * @param translatedQuery       the question translated into the index's language (2.3.0, cross-language
 *                              search), when the caller found that the question is written in another
 *                              language than most of the index. journey-service runs the full-text leg
 *                              on it (with that language's text-search configuration; the original
 *                              question's lexemes are ORed in) and counts the question's terms on it.
 *                              Null: the question as asked is the only text searched
 * @param translatedLocale      the language {@code translatedQuery} is in (e.g. {@code en}); null with
 *                              no translation
 * @param translatedQueryVector the vector of {@code translatedQuery}, made by {@code embeddingModel}
 *                              (2.3.0). The vector legs search with both vectors and a passage's
 *                              similarity is the better of the two cosines. Null with no translation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeSearchRequest(
        String indexName,
        float[] queryVector,
        int limit,
        String locale,
        String embeddingModel,
        UUID assistantId,
        String query,
        Double threshold,
        Double diversity,
        Boolean recall,
        String translatedQuery,
        String translatedLocale,
        float[] translatedQueryVector) {

    /** Without a translation (the 2.2.0 shape): the question as asked is the only text searched. */
    public KnowledgeSearchRequest(String indexName, float[] queryVector, int limit, String locale,
            String embeddingModel, UUID assistantId, String query, Double threshold, Double diversity,
            Boolean recall) {
        this(indexName, queryVector, limit, locale, embeddingModel, assistantId, query, threshold, diversity, recall,
                null, null, null);
    }

    /** Whether the caller sent a translation of the question ({@code translatedQuery}). */
    public boolean translated() {
        return translatedQuery != null && !translatedQuery.isBlank();
    }

    /** Without {@code recall}: a serving search, gated as usual. */
    public KnowledgeSearchRequest(String indexName, float[] queryVector, int limit, String locale,
            String embeddingModel, UUID assistantId, String query, Double threshold, Double diversity) {
        this(indexName, queryVector, limit, locale, embeddingModel, assistantId, query, threshold, diversity, null);
    }

    /**
     * The 2.1.0 shape: without the query text, the gate and diversity, so vector-only and
     * ungated.
     */
    public KnowledgeSearchRequest(String indexName, float[] queryVector, int limit, String locale,
            String embeddingModel, UUID assistantId) {
        this(indexName, queryVector, limit, locale, embeddingModel, assistantId, null, null, null, null);
    }

    /** Without the assistant: shared indexes only. */
    public KnowledgeSearchRequest(String indexName, float[] queryVector, int limit, String locale,
            String embeddingModel) {
        this(indexName, queryVector, limit, locale, embeddingModel, null);
    }

    /** Without the model — every chunk is a candidate, whichever model embedded it. */
    public KnowledgeSearchRequest(String indexName, float[] queryVector, int limit, String locale) {
        this(indexName, queryVector, limit, locale, null, null);
    }
}
