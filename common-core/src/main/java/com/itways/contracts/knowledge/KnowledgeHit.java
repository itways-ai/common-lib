package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One passage a search served, with where it came from and the evidence that admitted it: a hit
 * of {@link KnowledgeSearchExplanation}, and the fields of a citation in a reply's
 * {@code sources[]} (2.2.0).
 *
 * @param id           the passage (row) id
 * @param question     the passage's question, or its text for a document passage
 * @param answer       the stored answer; null for a document passage
 * @param locale       ISO 639-1, or null for a language-neutral passage
 * @param sourceKind   a {@code KnowledgeSourceView.KIND_*} value
 * @param rowNumber    the sheet row, when it came from a sheet
 * @param url          the page, when it came from a website
 * @param vectorScore  cosine similarity to the question, 0..1: since 2.2.0 the greater of the
 *                     passage's two vectors (see {@code vectorMatch}), the score the gate used
 * @param lexicalScore full-text rank, 0..1 (0 when the full-text search did not find it)
 * @param fusedScore   reciprocal-rank fusion of both rankings; orders hits, never admits one
 * @param reason       {@link #REASON_VECTOR} or {@link #REASON_LEXICAL_CORROBORATED}; in a recall
 *                     search also {@link #REASON_LEXICAL_RECALL}
 * @param matchedTerms how many of the question's distinct content terms the passage contains
 *                     (stemmed, Arabic verbs with their other conjugations; a term in most of the
 *                     index's passages does not count); null when the request had no text
 * @param termCoverage {@code matchedTerms} over the question's content terms, 0..1; null when the
 *                     request had no text or the question has no content term
 * @param questionScore cosine similarity of the query to the passage's question vector (the
 *                     question alone), 0..1 (2.2.0); null when the passage has none (it is not a
 *                     Q&amp;A row, or its question vector is not made yet)
 * @param vectorMatch  which vector gave {@code vectorScore} (2.2.0): {@link #VECTOR_MATCH_ANSWER}
 *                     (the passage's text, question and answer for a Q&amp;A row) or
 *                     {@link #VECTOR_MATCH_QUESTION}; null from a journey-service that predates it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeHit(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        String indexName,
        String question,
        String answer,
        String locale,
        Long sourceId,
        String sourceName,
        String sourceKind,
        Integer rowNumber,
        String url,
        Double vectorScore,
        Double lexicalScore,
        Double fusedScore,
        String reason,
        Integer matchedTerms,
        Double termCoverage,
        Double questionScore,
        String vectorMatch) {

    /** Served: the vector similarity reaches the threshold. */
    public static final String REASON_VECTOR = "VECTOR";
    /**
     * Served: slightly below the threshold (at most the relax), with full-text evidence that covers
     * the question ({@code matchedTerms}, {@code termCoverage}).
     */
    public static final String REASON_LEXICAL_CORROBORATED = "LEXICAL_CORROBORATED";
    /**
     * Served to a recall search only, i.e. to a composing step that hands its candidates to a
     * model (2.2.0): below the floor, on strong lexical evidence (the passage covers most of the
     * question's terms, or contains one of its rare terms). Never served as a stored answer.
     */
    public static final String REASON_LEXICAL_RECALL = "LEXICAL_RECALL";
    /** Not served: below the threshold (see {@link KnowledgeHitDrop}). */
    public static final String REASON_BELOW_THRESHOLD = "BELOW_THRESHOLD";

    /** {@code vectorScore} came from the passage's own vector (its text; question and answer of a Q&amp;A row). */
    public static final String VECTOR_MATCH_ANSWER = "ANSWER";
    /** {@code vectorScore} came from the question vector of a Q&amp;A row (its question alone). */
    public static final String VECTOR_MATCH_QUESTION = "QUESTION";

    /** Without the question vector's score and match (the 2.2.0 shape before they were added). */
    public KnowledgeHit(Long id, String indexName, String question, String answer, String locale, Long sourceId,
            String sourceName, String sourceKind, Integer rowNumber, String url, Double vectorScore,
            Double lexicalScore, Double fusedScore, String reason, Integer matchedTerms, Double termCoverage) {
        this(id, indexName, question, answer, locale, sourceId, sourceName, sourceKind, rowNumber, url, vectorScore,
                lexicalScore, fusedScore, reason, matchedTerms, termCoverage, null, null);
    }

    /** Without the term counts (the 2.2.0 shape before they were added). */
    public KnowledgeHit(Long id, String indexName, String question, String answer, String locale, Long sourceId,
            String sourceName, String sourceKind, Integer rowNumber, String url, Double vectorScore,
            Double lexicalScore, Double fusedScore, String reason) {
        this(id, indexName, question, answer, locale, sourceId, sourceName, sourceKind, rowNumber, url, vectorScore,
                lexicalScore, fusedScore, reason, null, null);
    }
}
