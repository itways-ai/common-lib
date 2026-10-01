package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One row to insert (no id) or update (id set). A null vector updates the
 * row's text and metadata but keeps its embedding.
 *
 * <p>
 * Since 2.2.0 an insert whose {@code contentHash} already exists in the same source updates that
 * row instead of adding a second one, so a repeated save cannot duplicate rows.
 *
 * @param embeddingModel which model made the vector (e.g. granite-embedding:278m); null when the
 *                       caller did not say, which journey-service treats as unknown
 * @param sourceId    the source the row belongs to (2.2.0); null: the index's typed-rows source
 * @param enabled     false keeps the row but never serves it (2.2.0); null leaves it as it is
 *                    (true for a new row)
 * @param contentHash {@code PassageHashes.sha256Hex(question, answer, locale)} (2.2.0); null lets
 *                    journey-service compute it
 * @param questionVector the question alone, embedded by the same model as {@code vector} (2.2.0).
 *                    On an update that sends a {@code vector} without it, journey-service clears
 *                    the row's question vector (the stale-vector catch-up fills it again); an
 *                    update without a {@code vector} keeps both
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatchUpsert(
        Long id,
        String question,
        String answer,
        String category,
        String notes,
        float[] vector,
        String locale,
        String embeddingModel,
        Long sourceId,
        Boolean enabled,
        String contentHash,
        float[] questionVector) {

    /** Without the question vector (the 2.2.0 shape before it was added). */
    public PatchUpsert(Long id, String question, String answer, String category, String notes, float[] vector,
            String locale, String embeddingModel, Long sourceId, Boolean enabled, String contentHash) {
        this(id, question, answer, category, notes, vector, locale, embeddingModel, sourceId, enabled, contentHash,
                null);
    }

    /** The 2.1.0 shape: no source, enabled flag or hash. */
    public PatchUpsert(Long id, String question, String answer, String category, String notes, float[] vector,
            String locale, String embeddingModel) {
        this(id, question, answer, category, notes, vector, locale, embeddingModel, null, null, null);
    }

    /** Without the model — for callers that predate it. */
    public PatchUpsert(Long id, String question, String answer, String category, String notes, float[] vector,
            String locale) {
        this(id, question, answer, category, notes, vector, locale, null);
    }
}
