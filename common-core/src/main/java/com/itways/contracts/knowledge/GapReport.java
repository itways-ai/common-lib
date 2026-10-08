package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

/**
 * A question the assistant could not answer well, as conversation-service reports it.
 *
 * <p>
 * Served by journey-service ({@code /api/knowledge-base}), called by
 * conversation-service. One definition, so the two cannot drift. Since 2.2.0 journey-service
 * scrubs e-mail addresses and phone numbers from the question before storing it
 * ({@code PiiScrubber}) and merges a report into a near-identical open gap.
 *
 * @param bestScore   similarity of the closest passage found, if any
 * @param bestPassage that passage, for the reviewer's context
 * @param vector      the question's embedding, used to group similar gaps
 * @param embeddingModel which model made the vector (e.g. granite-embedding:278m); null when the
 *                       caller did not say, which journey-service treats as unknown
 * @param indexNames  the indexes that were searched (2.2.0); null or empty when not known
 * @param source      what found the gap (2.2.0): {@link #SOURCE_KNOWLEDGE_STEP} or
 *                    {@link #SOURCE_FALLBACK}; null when not known
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GapReport(
        UUID assistantId,
        String question,
        String language,
        String channel,
        Double bestScore,
        String bestPassage,
        float[] vector,
        String embeddingModel,
        List<String> indexNames,
        String source) {

    /** A journey's knowledge step found nothing good enough. */
    public static final String SOURCE_KNOWLEDGE_STEP = "KNOWLEDGE_STEP";
    /** The fallback answer (no journey matched) found nothing good enough. */
    public static final String SOURCE_FALLBACK = "FALLBACK";

    /** The 2.1.0 shape: without the searched indexes and the source. */
    public GapReport(UUID assistantId, String question, String language, String channel, Double bestScore,
            String bestPassage, float[] vector, String embeddingModel) {
        this(assistantId, question, language, channel, bestScore, bestPassage, vector, embeddingModel, null, null);
    }

    /** Without the model — for callers that predate it. */
    public GapReport(UUID assistantId, String question, String language, String channel, Double bestScore,
            String bestPassage, float[] vector) {
        this(assistantId, question, language, channel, bestScore, bestPassage, vector, null);
    }
}
