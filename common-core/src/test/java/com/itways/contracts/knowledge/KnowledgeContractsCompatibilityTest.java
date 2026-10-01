package com.itways.contracts.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.itways.common.text.PassageHashes;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The 2.1.0 constructors of the records that gained fields in 2.2.0 still exist and give the
 * old behaviour; the new helpers build what the plan says. (The JSON side, old payloads read by
 * the new records and the reverse, is common-web's {@code KnowledgeContractsJsonTest}.)
 */
class KnowledgeContractsCompatibilityTest {

    private static final float[] VECTOR = { 0.1f, 0.2f };
    private static final UUID ASSISTANT = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Test
    void theOldSearchRequestIsVectorOnlyAndUngated() {
        KnowledgeSearchRequest six = new KnowledgeSearchRequest("faq", VECTOR, 5, "ar", "m", ASSISTANT);
        KnowledgeSearchRequest five = new KnowledgeSearchRequest("faq", VECTOR, 5, "ar", "m");
        KnowledgeSearchRequest four = new KnowledgeSearchRequest("faq", VECTOR, 5, "ar");

        for (KnowledgeSearchRequest request : List.of(six, five, four)) {
            assertThat(request.query()).isNull();
            assertThat(request.threshold()).isNull();
            assertThat(request.diversity()).isNull();
            assertThat(request.recall()).isNull();
        }
        assertThat(six.assistantId()).isEqualTo(ASSISTANT);
        assertThat(five.assistantId()).isNull();
        assertThat(four.embeddingModel()).isNull();

        KnowledgeSearchRequest hybrid = new KnowledgeSearchRequest("faq", VECTOR, 5, "ar", "m", ASSISTANT,
                "متى تفتحون؟", 0.7, 0.3);
        assertThat(hybrid.query()).isEqualTo("متى تفتحون؟");
        assertThat(hybrid.threshold()).isEqualTo(0.7);
        assertThat(hybrid.diversity()).isEqualTo(0.3);
        assertThat(hybrid.recall()).as("without recall: a serving search").isNull();
    }

    /** A recall search says so; the nine-argument constructor is a serving search. */
    @Test
    void aRecallSearchCarriesItsFlag() {
        KnowledgeSearchRequest recall = new KnowledgeSearchRequest("faq", VECTOR, 5, "ar", "m", ASSISTANT,
                "متى تفتحون؟", 0.55, null, true);
        assertThat(recall.recall()).isTrue();
        assertThat(recall.threshold()).isEqualTo(0.55);
        assertThat(new KnowledgeSearchRequest("faq", VECTOR, 5, "ar", "m", ASSISTANT, "q", 0.7, null).recall())
                .isNull();
    }

    /** Hits and drops built without the term counts leave them null. */
    @Test
    void hitsWithoutTermCountsLeaveThemNull() {
        KnowledgeHit hit = new KnowledgeHit(1L, "faq", "q", "a", "ar", 3L, "faq.xlsx", KnowledgeSourceView.KIND_SHEET,
                4, null, 0.82, 0.4, 0.03, KnowledgeHit.REASON_VECTOR);
        assertThat(hit.matchedTerms()).isNull();
        assertThat(hit.termCoverage()).isNull();
        KnowledgeHitDrop drop = new KnowledgeHitDrop(2L, "other", 0.5, 0.1, KnowledgeHit.REASON_BELOW_THRESHOLD);
        assertThat(drop.matchedTerms()).isNull();
        assertThat(drop.termCoverage()).isNull();
        assertThat(new KnowledgeHitDrop(2L, "other", 0.5, 0.1, KnowledgeHit.REASON_BELOW_THRESHOLD, 1, 0.5)
                .termCoverage()).isEqualTo(0.5);
    }

    /** Hits and drops built without the question vector's score leave it and the match null. */
    @Test
    void hitsWithoutTheQuestionScoreLeaveItNull() {
        KnowledgeHit sixteen = new KnowledgeHit(1L, "faq", "q", "a", "ar", 3L, "faq.xlsx",
                KnowledgeSourceView.KIND_SHEET, 4, null, 0.82, 0.4, 0.03, KnowledgeHit.REASON_VECTOR, 1, 0.5);
        KnowledgeHit fourteen = new KnowledgeHit(1L, "faq", "q", "a", "ar", 3L, "faq.xlsx",
                KnowledgeSourceView.KIND_SHEET, 4, null, 0.82, 0.4, 0.03, KnowledgeHit.REASON_VECTOR);
        for (KnowledgeHit hit : List.of(sixteen, fourteen)) {
            assertThat(hit.questionScore()).isNull();
            assertThat(hit.vectorMatch()).isNull();
        }
        assertThat(sixteen.termCoverage()).isEqualTo(0.5);

        KnowledgeHit dual = new KnowledgeHit(1L, "faq", "q", "a", "ar", 3L, "faq.xlsx", KnowledgeSourceView.KIND_QA,
                null, null, 0.71, 0.0, 0.03, KnowledgeHit.REASON_LEXICAL_RECALL, 2, 1.0, 0.71,
                KnowledgeHit.VECTOR_MATCH_QUESTION);
        assertThat(dual.questionScore()).isEqualTo(0.71);
        assertThat(dual.vectorMatch()).isEqualTo("QUESTION");
        assertThat(KnowledgeHit.VECTOR_MATCH_ANSWER).isEqualTo("ANSWER");
        assertThat(KnowledgeHit.REASON_LEXICAL_RECALL).isEqualTo("LEXICAL_RECALL");

        KnowledgeHitDrop seven = new KnowledgeHitDrop(2L, "other", 0.5, 0.1, KnowledgeHit.REASON_BELOW_THRESHOLD, 1,
                0.5);
        KnowledgeHitDrop five = new KnowledgeHitDrop(2L, "other", 0.5, 0.1, KnowledgeHit.REASON_BELOW_THRESHOLD);
        KnowledgeHitDrop four = new KnowledgeHitDrop(2L, 0.5, 0.1, KnowledgeHit.REASON_BELOW_THRESHOLD);
        for (KnowledgeHitDrop drop : List.of(seven, five, four)) {
            assertThat(drop.questionScore()).isNull();
            assertThat(drop.vectorMatch()).isNull();
        }
        assertThat(new KnowledgeHitDrop(2L, "other", 0.5, 0.1, KnowledgeHit.REASON_BELOW_THRESHOLD, 1, 0.5, 0.31,
                KnowledgeHit.VECTOR_MATCH_ANSWER).questionScore()).isEqualTo(0.31);
    }

    /** The records that gained the question vector: the older constructors leave it (and its text) null. */
    @Test
    void theOlderShapesCarryNoQuestionVector() {
        assertThat(new PendingPassage(9L, "q\na").questionText()).isNull();
        assertThat(new PendingPassage(9L, "q\na", "q").questionText()).isEqualTo("q");
        assertThat(new PassageVectors.Vector(9L, VECTOR).questionVector()).isNull();

        StalePassages.Passage two = new StalePassages.Passage(9L, "text");
        assertThat(two.questionText()).isNull();
        assertThat(two.ignoreTerms()).isNull();
        StalePassages.Passage four = new StalePassages.Passage(9L, "q\na", "q", List.of("Cast Farm"));
        assertThat(four.questionText()).isEqualTo("q");
        assertThat(four.ignoreTerms()).containsExactly("Cast Farm");

        PatchUpsert eleven = new PatchUpsert(1L, "q", "a", null, null, VECTOR, "en", "m", 9L, true, "h");
        assertThat(eleven.sourceId()).isEqualTo(9L);
        assertThat(eleven.contentHash()).isEqualTo("h");
        for (PatchUpsert upsert : List.of(eleven, new PatchUpsert(1L, "q", "a", null, null, VECTOR, "en", "m"),
                new PatchUpsert(null, "q", "a", null, null, null, "en"))) {
            assertThat(upsert.questionVector()).isNull();
        }

        KnowledgeChunk eight = new KnowledgeChunk("q", "a", null, null, 2, VECTOR, "en", "m");
        assertThat(eight.embeddingModel()).isEqualTo("m");
        assertThat(eight.questionVector()).isNull();
        assertThat(new KnowledgeChunk("q", "a", null, null, 2, VECTOR, "en").questionVector()).isNull();

        GapApproveRequest fromPortal = new GapApproveRequest(List.of(1L), "faq", "q", "a", "ar", null, null,
                ASSISTANT);
        assertThat(fromPortal.questionVector()).isNull();
        assertThat(fromPortal.withVector(VECTOR, "m").questionVector()).isNull();
    }

    /** The question vector rides alongside the answer vector, made by the same model. */
    @Test
    void theNewShapesCarryTheQuestionVector() {
        float[] question = { 0.3f, 0.4f };

        assertThat(new PassageVectors.Vector(9L, VECTOR, question).questionVector()).containsExactly(question);
        assertThat(new PatchUpsert(null, "q", "a", null, null, VECTOR, "en", "m", 9L, true, "h", question)
                .questionVector()).containsExactly(question);
        assertThat(new KnowledgeChunk("q", "a", null, null, 2, VECTOR, "en", "m", question).questionVector())
                .containsExactly(question);

        GapApproveRequest embedded = new GapApproveRequest(List.of(1L, 2L), "faq", "q", "a", "ar", null, null,
                ASSISTANT).withVectors(VECTOR, question, "m");
        assertThat(embedded.vector()).containsExactly(VECTOR);
        assertThat(embedded.questionVector()).containsExactly(question);
        assertThat(embedded.embeddingModel()).isEqualTo("m");
        assertThat(embedded.gapIds()).containsExactly(1L, 2L);
        assertThat(embedded.assistantId()).isEqualTo(ASSISTANT);
        assertThat(embedded.withVector(VECTOR, "m").questionVector()).as("withVector drops it").isNull();
    }

    @Test
    void theOldIndexSummaryIsReadyWithItsRowsAsPassages() {
        KnowledgeIndexSummary summary = new KnowledgeIndexSummary(7L, "faq", null, true, "d", 12);

        assertThat(summary.passages()).isEqualTo(12);
        assertThat(summary.rowCount()).isEqualTo(12);
        assertThat(summary.sources()).isZero();
        assertThat(summary.pending()).isZero();
        assertThat(summary.status()).isEqualTo(KnowledgeIndexSummary.STATUS_READY);
        assertThat(summary.updatedAt()).isNull();
        assertThat(summary.legacyName()).isFalse();
        assertThat(new KnowledgeIndexSummary(8L, "My FAQ", ASSISTANT, false, null, 0).legacyName()).isTrue();
        assertThat(summary.ignoreTerms()).isEmpty();
    }

    /** Ignore terms: none from the older constructors, never null, null entries dropped. */
    @Test
    void indexSummariesCarryTheirIgnoreTerms() {
        KnowledgeIndexSummary twelve = new KnowledgeIndexSummary(7L, "faq", null, true, "d", 12, 1, 12, 0,
                KnowledgeIndexSummary.STATUS_READY, null, false);
        assertThat(twelve.ignoreTerms()).isEmpty();

        KnowledgeIndexSummary withTerms = new KnowledgeIndexSummary(7L, "faq", null, true, "d", 12, 1, 12, 0,
                KnowledgeIndexSummary.STATUS_READY, null, false, Arrays.asList("Cast Farm", null, "كاست فارم"));
        assertThat(withTerms.ignoreTerms()).containsExactly("Cast Farm", "كاست فارم");
        assertThat(new KnowledgeIndexSummary(7L, "faq", null, true, "d", 12, 1, 12, 0,
                KnowledgeIndexSummary.STATUS_READY, null, false, null).ignoreTerms()).isEmpty();
        assertThat(IndexSettingsRequest.MAX_TERMS).isEqualTo(20);
        assertThat(IndexSettingsRequest.MAX_TERM_LENGTH).isEqualTo(60);
    }

    @Test
    void theOldRowIsEnabledWithoutASource() {
        KnowledgeRow row = new KnowledgeRow(1L, "q", "a", "c", "n", 3);

        assertThat(row.enabled()).isTrue();
        assertThat(row.sourceId()).isNull();
        assertThat(row.contentHash()).isNull();
        assertThat(row.rowNumber()).isEqualTo(3);
    }

    @Test
    void theOldUpsertsLeaveSourceEnabledAndHashToJourney() {
        PatchUpsert eight = new PatchUpsert(1L, "q", "a", null, null, VECTOR, "en", "m");
        PatchUpsert seven = new PatchUpsert(null, "q", "a", null, null, null, "en");

        assertThat(eight.embeddingModel()).isEqualTo("m");
        assertThat(seven.embeddingModel()).isNull();
        for (PatchUpsert upsert : List.of(eight, seven)) {
            assertThat(upsert.sourceId()).isNull();
            assertThat(upsert.enabled()).isNull();
            assertThat(upsert.contentHash()).isNull();
        }
    }

    @Test
    void theOldGapShapes() {
        GapReport eight = new GapReport(ASSISTANT, "q", "ar", "web", 0.4, "p", VECTOR, "m");
        GapReport seven = new GapReport(ASSISTANT, "q", "ar", "web", 0.4, "p", VECTOR);
        assertThat(eight.indexNames()).isNull();
        assertThat(eight.source()).isNull();
        assertThat(seven.embeddingModel()).isNull();

        assertThat(new GapRecorded(5L).merged()).isFalse();
        assertThat(new GapRecorded(5L, true).merged()).isTrue();

        LocalDateTime now = LocalDateTime.of(2026, 9, 29, 12, 0);
        GapGroup group = new GapGroup(1L, "q", 3, List.of("ar"), List.of("web"), List.of("q"), List.of(1L, 2L, 3L),
                now, now);
        assertThat(group.hitCount()).isEqualTo(3);
        assertThat(group.indexNames()).isEmpty();
        assertThat(group.source()).isNull();
    }

    @Test
    void sourceViewsCarryTheUploadNotesOnlyWhenAdded() {
        LocalDateTime created = LocalDateTime.of(2026, 9, 29, 10, 0);
        KnowledgeSourceView view = new KnowledgeSourceView(3L, KnowledgeSourceView.KIND_SHEET, "faq.xlsx", null,
                KnowledgeSourceView.STATUS_QUEUED, null, null, 40, 40, 0, null, 0, created, created, null);

        assertThat(view.dropped()).isNull();
        assertThat(view.droppedRows()).isNull();
        assertThat(view.truncated()).isNull();

        KnowledgeSourceView noted = view.withUploadNotes(1, List.of(new DroppedRow(7, DroppedRow.NO_ANSWER)), false);
        assertThat(noted.dropped()).isEqualTo(1);
        assertThat(noted.droppedRows()).containsExactly(new DroppedRow(7, null, DroppedRow.NO_ANSWER));
        assertThat(noted.truncated()).isFalse();
        assertThat(noted.name()).isEqualTo("faq.xlsx");
        assertThat(noted.passages()).isEqualTo(40);
        assertThat(noted.ignoreTerms()).isNull();
    }

    /** Only a claim answer carries the index's ignore terms; the upload notes keep them. */
    @Test
    void sourceViewsCarryIgnoreTermsOnlyWhenAdded() {
        LocalDateTime created = LocalDateTime.of(2026, 9, 29, 10, 0);
        KnowledgeSourceView eighteen = new KnowledgeSourceView(3L, KnowledgeSourceView.KIND_SHEET, "faq.xlsx", null,
                KnowledgeSourceView.STATUS_QUEUED, null, null, 40, 40, 0, null, 0, created, created, null, 1, List.of(),
                false);
        assertThat(eighteen.ignoreTerms()).isNull();
        assertThat(eighteen.dropped()).isEqualTo(1);

        KnowledgeSourceView claimed = eighteen.withIgnoreTerms(List.of("Cast Farm", "CastFarm"));
        assertThat(claimed.ignoreTerms()).containsExactly("Cast Farm", "CastFarm");
        assertThat(claimed.name()).isEqualTo("faq.xlsx");
        assertThat(claimed.withUploadNotes(null, null, null).ignoreTerms()).containsExactly("Cast Farm", "CastFarm");
        assertThat(claimed.withIgnoreTerms(null).ignoreTerms()).isNull();
    }

    @Test
    @SuppressWarnings("removal")
    void sourceViewsAnswerTheOldSourceFields() {
        LocalDateTime created = LocalDateTime.of(2026, 9, 29, 10, 0);
        LocalDateTime ingested = LocalDateTime.of(2026, 9, 29, 10, 5);
        KnowledgeSourceView ready = new KnowledgeSourceView(3L, KnowledgeSourceView.KIND_DOCUMENT, "guide.pdf", null,
                KnowledgeSourceView.STATUS_READY, null, null, 12, 0, 100, "h", 1, created, created, ingested);
        KnowledgeSourceView queued = new KnowledgeSourceView(4L, KnowledgeSourceView.KIND_DOCUMENT, "new.pdf", null,
                KnowledgeSourceView.STATUS_QUEUED, null, null, 3, 3, 0, null, 0, created, created, null);

        assertThat(ready.sourceFile()).isEqualTo("guide.pdf");
        assertThat(ready.chunks()).isEqualTo(12);
        assertThat(ready.lastIngested()).isEqualTo(ingested);
        assertThat(queued.lastIngested()).isEqualTo(created);
    }

    @Test
    void helpersBuildWhatTheWorkerAndJourneyExchange() {
        SourcePassage passage = SourcePassage.of("  Opening hours ", "9 to 5", null, null, 4, "en");
        assertThat(passage.contentHash()).isEqualTo(PassageHashes.sha256Hex("Opening hours", "9 to 5", "en"));
        assertThat(passage.text()).isEqualTo("  Opening hours ");

        CreateSourceRequest website = CreateSourceRequest.website("https://example.com/hours");
        assertThat(website.kind()).isEqualTo(KnowledgeSourceView.KIND_WEBSITE);
        assertThat(website.name()).isEqualTo(website.url()).isEqualTo("https://example.com/hours");
        assertThat(website.passages()).isNull();

        assertThat(SourcePatch.heartbeat(40, 120)).isEqualTo(new SourcePatch(null, 40, null, null, null, 120));
        assertThat(SourcePatch.ready("h")).isEqualTo(new SourcePatch("READY", 100, null, null, "h", null));
        assertThat(SourcePatch.failed("SOURCE_UNREADABLE", "We could not read this file."))
                .isEqualTo(new SourcePatch("FAILED", null, "SOURCE_UNREADABLE", "We could not read this file.", null,
                        null));

        GapApproveRequest fromPortal = new GapApproveRequest(List.of(1L, 2L), "faq", "q", "a", "ar", null, null,
                ASSISTANT);
        GapApproveRequest embedded = fromPortal.withVector(VECTOR, "granite-embedding:278m");
        assertThat(embedded.vector()).containsExactly(VECTOR);
        assertThat(embedded.embeddingModel()).isEqualTo("granite-embedding:278m");
        assertThat(embedded.gapIds()).containsExactly(1L, 2L);
        assertThat(embedded.assistantId()).isEqualTo(ASSISTANT);

        KnowledgeSearchExplanation explanation = new KnowledgeSearchExplanation(List.of(), List.of(), 0.7, 0.05,
                "arabic", null, 0, 12);
        assertThat(explanation.withEmbeddingMs(30).embeddingMs()).isEqualTo(30);
        assertThat(explanation.withEmbeddingMs(30).searchMs()).isEqualTo(12);

        assertThat(new KnowledgeHitDrop(9L, 0.62, 0.0, KnowledgeHit.REASON_BELOW_THRESHOLD).question()).isNull();
        assertThat(new CreateIndexRequest("faq", "Questions").shared()).isNull();
    }
}
