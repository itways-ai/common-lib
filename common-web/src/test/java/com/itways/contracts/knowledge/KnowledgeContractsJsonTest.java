package com.itways.contracts.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The 2.2.0 knowledge contracts on the wire, with the services' kind of mapper: a payload of a
 * 2.1.0 service reads into the new records with the old meaning, a 2.2.0 payload reads into the
 * 2.1.0 shapes (extra fields ignored), and every new record survives a round trip.
 */
@SuppressWarnings("removal")
class KnowledgeContractsJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final UUID ASSISTANT = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 9, 29, 10, 0);
    private static final LocalDateTime INGESTED = LocalDateTime.of(2026, 9, 29, 10, 5);

    /** KnowledgeSearchRequest as 2.1.0 declared it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchRequest210(String indexName, float[] queryVector, int limit, String locale, String embeddingModel,
            UUID assistantId) {
    }

    /** KnowledgeRow as 2.1.0 declared it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Row210(Long id, String question, String answer, String category, String notes, int rowNumber) {
    }

    /** PassageVectors.Vector before the question vector. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record VectorWithoutQuestion(Long id, float[] vector) {
    }

    /** StalePassages.Passage before the question text and the index's terms. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PassageWithoutQuestion(long id, String text) {
    }

    private static <T> T read(String json, Class<T> type) throws Exception {
        return MAPPER.readValue(json, type);
    }

    private static <T> T roundTrip(T value, Class<T> type) throws Exception {
        return MAPPER.readValue(MAPPER.writeValueAsString(value), type);
    }

    @Test
    void anOldSearchRequestReadsAsVectorOnlyAndUngated() throws Exception {
        KnowledgeSearchRequest request = read("""
                {"indexName":"faq","queryVector":[0.1,0.2],"limit":5,"locale":"ar","embeddingModel":"m",
                 "assistantId":"00000000-0000-0000-0000-000000000042"}""", KnowledgeSearchRequest.class);

        assertThat(request.indexName()).isEqualTo("faq");
        assertThat(request.queryVector()).containsExactly(0.1f, 0.2f);
        assertThat(request.assistantId()).isEqualTo(ASSISTANT);
        assertThat(request.query()).isNull();
        assertThat(request.threshold()).isNull();
        assertThat(request.diversity()).isNull();
        assertThat(request.recall()).isNull();
    }

    /** {@code recall} on the wire: sent as a boolean, absent when the caller does not say. */
    @Test
    void aRecallSearchRequestSurvivesTheWire() throws Exception {
        String json = MAPPER.writeValueAsString(new KnowledgeSearchRequest("faq", new float[] { 0.5f }, 3, "ar", "m",
                ASSISTANT, "متى تفتحون؟", 0.55, null, true));
        assertThat(MAPPER.readTree(json).get("recall").asBoolean()).isTrue();
        assertThat(read(json, KnowledgeSearchRequest.class).recall()).isTrue();
        assertThat(read(json, SearchRequest210.class).indexName()).isEqualTo("faq");
        assertThat(read("""
                {"indexName":"faq","queryVector":[0.1],"limit":5,"query":"q","threshold":0.55,"recall":false}""",
                KnowledgeSearchRequest.class).recall()).isFalse();
    }

    /** The term counts of a hit and a drop: absent in an older payload, carried when present. */
    @Test
    void hitTermCountsAreOptionalOnTheWire() throws Exception {
        KnowledgeHit old = read("""
                {"id":1,"indexName":"faq","question":"q","vectorScore":0.8,"lexicalScore":0.2,"reason":"VECTOR"}""",
                KnowledgeHit.class);
        assertThat(old.matchedTerms()).isNull();
        assertThat(old.termCoverage()).isNull();
        KnowledgeHit hit = new KnowledgeHit(1L, "faq", "q", "a", "ar", 3L, "faq.xlsx", KnowledgeSourceView.KIND_SHEET,
                4, null, 0.66, 0.29, 0.03, KnowledgeHit.REASON_LEXICAL_CORROBORATED, 1, 1.0);
        JsonNode node = MAPPER.readTree(MAPPER.writeValueAsString(hit));
        assertThat(node.get("matchedTerms").asInt()).isEqualTo(1);
        assertThat(node.get("termCoverage").asDouble()).isEqualTo(1.0);
        assertThat(roundTrip(hit, KnowledgeHit.class)).isEqualTo(hit);
        KnowledgeHitDrop drop = new KnowledgeHitDrop(2L, "other", 0.67, 0.1, KnowledgeHit.REASON_BELOW_THRESHOLD, 1,
                0.25);
        assertThat(roundTrip(drop, KnowledgeHitDrop.class)).isEqualTo(drop);
    }

    /** The question vector's score and which vector matched: absent in an older payload, carried when present. */
    @Test
    void hitVectorMatchIsOptionalOnTheWire() throws Exception {
        KnowledgeHit old = read("""
                {"id":1,"indexName":"faq","question":"q","vectorScore":0.8,"reason":"VECTOR","matchedTerms":1,
                 "termCoverage":0.5}""", KnowledgeHit.class);
        assertThat(old.termCoverage()).isEqualTo(0.5);
        assertThat(old.questionScore()).isNull();
        assertThat(old.vectorMatch()).isNull();
        KnowledgeHitDrop oldDrop = read("""
                {"id":2,"question":"other","vectorScore":0.5,"reason":"BELOW_THRESHOLD","matchedTerms":0}""",
                KnowledgeHitDrop.class);
        assertThat(oldDrop.questionScore()).isNull();
        assertThat(oldDrop.vectorMatch()).isNull();

        KnowledgeHit hit = new KnowledgeHit(1L, "faq", "q", "a", "ar", 3L, "Typed rows", KnowledgeSourceView.KIND_QA,
                null, null, 0.31, 0.4, 0.03, KnowledgeHit.REASON_LEXICAL_RECALL, 2, 1.0, 0.31,
                KnowledgeHit.VECTOR_MATCH_QUESTION);
        JsonNode node = MAPPER.readTree(MAPPER.writeValueAsString(hit));
        assertThat(node.get("questionScore").asDouble()).isEqualTo(0.31);
        assertThat(node.get("vectorMatch").asText()).isEqualTo("QUESTION");
        assertThat(node.get("reason").asText()).isEqualTo("LEXICAL_RECALL");
        assertThat(roundTrip(hit, KnowledgeHit.class)).isEqualTo(hit);
        KnowledgeHitDrop drop = new KnowledgeHitDrop(2L, "other", 0.12, 0.0, KnowledgeHit.REASON_BELOW_THRESHOLD, 0,
                0.0, null, KnowledgeHit.VECTOR_MATCH_ANSWER);
        assertThat(roundTrip(drop, KnowledgeHitDrop.class)).isEqualTo(drop);
        KnowledgeSearchExplanation explanation = new KnowledgeSearchExplanation(List.of(hit), List.of(drop), 0.7,
                0.05, "arabic", "a", 12, 30);
        assertThat(roundTrip(explanation, KnowledgeSearchExplanation.class)).isEqualTo(explanation);
    }

    @Test
    void aNewSearchRequestReadsIntoTheOldShape() throws Exception {
        String json = MAPPER.writeValueAsString(new KnowledgeSearchRequest("faq", new float[] { 0.5f }, 3, null, "m",
                ASSISTANT, "متى تفتحون؟", 0.7, 0.3));

        assertThat(MAPPER.readTree(json).get("query").asText()).isEqualTo("متى تفتحون؟");
        assertThat(MAPPER.readTree(json).get("threshold").asDouble()).isEqualTo(0.7);
        SearchRequest210 old = read(json, SearchRequest210.class);
        assertThat(old.indexName()).isEqualTo("faq");
        assertThat(old.limit()).isEqualTo(3);

        KnowledgeSearchRequest back = read(json, KnowledgeSearchRequest.class);
        assertThat(back.query()).isEqualTo("متى تفتحون؟");
        assertThat(back.threshold()).isEqualTo(0.7);
        assertThat(back.diversity()).isEqualTo(0.3);
    }

    @Test
    void anOldRowReadsWithoutClaimingItIsDisabled() throws Exception {
        KnowledgeRow row = read("""
                {"id":1,"question":"q","answer":"a","category":null,"notes":null,"rowNumber":2}""", KnowledgeRow.class);

        assertThat(row.enabled()).as("absent, not false").isNull();
        assertThat(row.sourceId()).isNull();

        String json = MAPPER.writeValueAsString(new KnowledgeRow(1L, "q", "a", null, null, 2, 9L, "faq.xlsx",
                KnowledgeSourceView.KIND_SHEET, null, "ar", false, "h"));
        assertThat(read(json, Row210.class)).isEqualTo(new Row210(1L, "q", "a", null, null, 2));
        assertThat(read(json, KnowledgeRow.class).enabled()).isFalse();
    }

    @Test
    void anOldIndexSummaryAndGapReadWithNeutralDefaults() throws Exception {
        KnowledgeIndexSummary summary = read("""
                {"id":7,"name":"faq","assistantId":null,"shared":true,"description":"d","rowCount":12}""",
                KnowledgeIndexSummary.class);
        assertThat(summary.rowCount()).isEqualTo(12);
        assertThat(summary.status()).isNull();
        assertThat(summary.legacyName()).isFalse();
        assertThat(summary.ignoreTerms()).as("absent reads as none").isEmpty();

        assertThat(read("{\"id\":5}", GapRecorded.class)).isEqualTo(new GapRecorded(5L, false));

        GapGroup group = read("""
                {"id":1,"question":"q","count":2,"languages":["ar"],"channels":["web"],"examples":["q"],
                 "gapIds":[1,2],"firstAsked":"2026-09-29T10:00:00","lastAsked":"2026-09-29T10:05:00"}""",
                GapGroup.class);
        assertThat(group.count()).isEqualTo(2);
        assertThat(group.indexNames()).isNull();
        assertThat(group.lastAsked()).isEqualTo(INGESTED);

        GapReport report = read("""
                {"assistantId":"00000000-0000-0000-0000-000000000042","question":"q","vector":[0.1]}""",
                GapReport.class);
        assertThat(report.indexNames()).isNull();
        assertThat(report.source()).isNull();
    }

    /** A 2.2.0 parsed sheet (rows, dropped, droppedRows only) reads; the 2.3.0 structure survives the wire. */
    @Test
    void aParsedSheetWithoutStructureStillReads() throws Exception {
        ParsedSheet old = read("""
                {"rows":[{"question":"q","answer":"a","category":null,"notes":"","rowNumber":2}],"dropped":1,
                 "droppedRows":[{"rowNumber":3,"question":"x?","reason":"NO_ANSWER"}]}""", ParsedSheet.class);
        assertThat(old.rows()).containsExactly(new ParsedRow("q", "a", null, "", 2));
        assertThat(old.dropped()).isEqualTo(1);
        assertThat(old.kind()).isNull();
        assertThat(old.passages()).isZero();
        assertThat(old.truncated()).isFalse();
        assertThat(old.sheets()).isNull();

        ParsedBlock qa = new ParsedBlock(null, ParsedBlock.MODE_QA, "A1:C3", 1, List.of("Question", "Answer", "Notes"),
                List.of(ParsedBlock.ROLE_QUESTION, ParsedBlock.ROLE_ANSWER, ParsedBlock.ROLE_NOTES), 1.0, 2, 2,
                List.of(List.of("q", "a", ""), List.of("q2", "a2", "n")));
        ParsedBlock prose = new ParsedBlock("About us", ParsedBlock.MODE_PROSE, "A5:A9", null, List.of("A"),
                List.of(ParsedBlock.ROLE_OTHER), 0.0, 4, 1, List.of(List.of("Line one"), List.of("Line two")));
        ParsedSheet sheet = new ParsedSheet(List.of(new ParsedRow("q", "a", null, null, 2)), 0, List.of(),
                KnowledgeSourceView.KIND_SHEET, 3, true,
                List.of(new ParsedWorksheet("FAQ", 0, false, 9, 3, List.of(qa, prose)),
                        new ParsedWorksheet("Draft", 1, true, 0, 0, List.of())));
        JsonNode node = MAPPER.readTree(MAPPER.writeValueAsString(sheet));
        assertThat(node.get("kind").asText()).isEqualTo("SHEET");
        assertThat(node.get("passages").asInt()).isEqualTo(3);
        assertThat(node.get("truncated").asBoolean()).isTrue();
        assertThat(node.get("sheets").get(0).get("blocks").get(0).get("mode").asText()).isEqualTo("QA");
        assertThat(node.get("sheets").get(0).get("blocks").get(0).get("headerRow").asInt()).isEqualTo(1);
        assertThat(node.get("sheets").get(0).get("blocks").get(1).get("headerRow").isNull()).isTrue();
        assertThat(node.get("sheets").get(0).get("blocks").get(1).get("title").asText()).isEqualTo("About us");
        assertThat(node.get("sheets").get(0).get("blocks").get(0).get("samples").get(1).get(2).asText()).isEqualTo("n");
        assertThat(node.get("sheets").get(1).get("hidden").asBoolean()).isTrue();
        assertThat(node.get("sheets").get(1).get("blocks")).isEmpty();
        assertThat(roundTrip(sheet, ParsedSheet.class)).isEqualTo(sheet);
    }

    @Test
    void aSourceViewStillAnswersAsTheOldSourceForOneRelease() throws Exception {
        KnowledgeSourceView view = new KnowledgeSourceView(3L, KnowledgeSourceView.KIND_DOCUMENT, "guide.pdf", null,
                KnowledgeSourceView.STATUS_READY, null, null, 12, 0, 100, "h", 1, CREATED, CREATED, INGESTED);
        String json = MAPPER.writeValueAsString(view);

        JsonNode node = MAPPER.readTree(json);
        assertThat(node.get("sourceFile").asText()).isEqualTo("guide.pdf");
        assertThat(node.get("chunks").asLong()).isEqualTo(12);
        assertThat(node.get("lastIngested").asText()).isEqualTo("2026-09-29T10:05:00");

        // What a 2.1.0 reader of GET /{index}/sources gets.
        KnowledgeSource old = read(json, KnowledgeSource.class);
        assertThat(old).isEqualTo(new KnowledgeSource("guide.pdf", 12, INGESTED));

        // And the aliases are never read back.
        assertThat(read(json, KnowledgeSourceView.class)).isEqualTo(view);
    }

    @Test
    void everyNewRecordSurvivesARoundTrip() throws Exception {
        KnowledgeSourceView view = new KnowledgeSourceView(3L, KnowledgeSourceView.KIND_SHEET, "faq.xlsx", null,
                KnowledgeSourceView.STATUS_QUEUED, null, null, 40, 40, 0, null, 0, CREATED, CREATED, null)
                .withUploadNotes(1, List.of(new DroppedRow(7, "q?", DroppedRow.NO_ANSWER)), true);
        assertThat(roundTrip(view, KnowledgeSourceView.class)).isEqualTo(view);

        KnowledgeHit hit = new KnowledgeHit(1L, "faq", "q", "a", "ar", 3L, "faq.xlsx", KnowledgeSourceView.KIND_SHEET, 4,
                null, 0.82, 0.4, 0.03, KnowledgeHit.REASON_VECTOR);
        KnowledgeSearchExplanation explanation = new KnowledgeSearchExplanation(List.of(hit),
                List.of(new KnowledgeHitDrop(2L, "other", 0.5, 0.0, KnowledgeHit.REASON_BELOW_THRESHOLD)), 0.7, 0.05,
                "arabic", "a", 12, 30);
        assertThat(roundTrip(explanation, KnowledgeSearchExplanation.class)).isEqualTo(explanation);

        List<Object> records = List.of(
                new ParsedSheet(List.of(new ParsedRow("q", "a", null, null, 2)), 1,
                        List.of(new DroppedRow(3, DroppedRow.NO_ANSWER))),
                new CreateSourceRequest(KnowledgeSourceView.KIND_QA, "Typed rows", null,
                        List.of(SourcePassage.of("q", "a", null, null, null, "ar"))),
                CreateSourceRequest.website("https://example.com/"),
                new SourceDiff(1, 2, 3),
                new ClaimRequest("host:42", 120, 2),
                SourcePatch.heartbeat(50, 120), SourcePatch.ready("h"), SourcePatch.failed("INTERRUPTED", "Stopped."),
                new PendingPassage(9L, "text"),
                new PendingPassage(10L, "q\na", "q"),
                new StalePassages(List.of(new StalePassages.Passage(9L, "text"),
                        new StalePassages.Passage(10L, "q\na", "q", List.of("Cast Farm", "كاست فارم"))), 2),
                new KnowledgeIndexSummary(7L, "faq", ASSISTANT, false, null, 3, 1, 3, 1,
                        KnowledgeIndexSummary.STATUS_PROCESSING, CREATED, false),
                new CreateIndexRequest("faq", "Questions", false, ASSISTANT),
                new KnowledgeRow(1L, "q", "a", null, null, 2, 9L, "faq.xlsx", KnowledgeSourceView.KIND_SHEET, null, "ar",
                        true, "h"),
                new RowsBulkRequest(List.of(1L, 2L), RowsBulkRequest.ACTION_DISABLE),
                new RowsBulkResult(2),
                new GapRecorded(5L, true),
                new GapApproved(2, 11L, false),
                new GapGroup(1L, "q", 2, List.of("ar"), List.of("web"), List.of("q"), List.of(1L, 2L), CREATED, INGESTED,
                        5, List.of("faq"), GapReport.SOURCE_KNOWLEDGE_STEP));
        for (Object record : records) {
            Object back = MAPPER.readValue(MAPPER.writeValueAsString(record), record.getClass());
            assertThat(back).as(record.getClass().getSimpleName()).isEqualTo(record);
        }
    }

    @Test
    void recordsWithVectorsSurviveARoundTrip() throws Exception {
        PatchUpsert upsert = roundTrip(new PatchUpsert(null, "q", "a", null, null, new float[] { 0.25f }, "ar", "m",
                9L, false, "h"), PatchUpsert.class);
        assertThat(upsert.vector()).containsExactly(0.25f);
        assertThat(upsert.sourceId()).isEqualTo(9L);
        assertThat(upsert.enabled()).isFalse();
        assertThat(upsert.contentHash()).isEqualTo("h");

        GapReport report = roundTrip(new GapReport(ASSISTANT, "q", "ar", "web", 0.4, "p", new float[] { 0.5f }, "m",
                List.of("faq"), GapReport.SOURCE_FALLBACK), GapReport.class);
        assertThat(report.indexNames()).containsExactly("faq");
        assertThat(report.source()).isEqualTo(GapReport.SOURCE_FALLBACK);

        GapApproveRequest approve = roundTrip(new GapApproveRequest(List.of(1L), "faq", "q", "a", "ar", null, null,
                ASSISTANT).withVector(new float[] { 0.75f }, "m"), GapApproveRequest.class);
        assertThat(approve.vector()).containsExactly(0.75f);
        assertThat(approve.embeddingModel()).isEqualTo("m");
        assertThat(approve.assistantId()).isEqualTo(ASSISTANT);
        assertThat(approve.questionVector()).isNull();
    }

    /** Question vectors and texts: absent in an older payload (null), carried when present. */
    @Test
    void questionVectorsAreOptionalOnTheWire() throws Exception {
        assertThat(read("{\"id\":9,\"text\":\"t\"}", PendingPassage.class).questionText()).isNull();
        assertThat(roundTrip(new PendingPassage(9L, "q\na", "q"), PendingPassage.class).questionText())
                .isEqualTo("q");

        PassageVectors oldVectors = read("""
                {"embeddingModel":"m","vectors":[{"id":9,"vector":[0.5]}]}""", PassageVectors.class);
        assertThat(oldVectors.vectors().get(0).vector()).containsExactly(0.5f);
        assertThat(oldVectors.vectors().get(0).questionVector()).isNull();
        String vectors = MAPPER.writeValueAsString(new PassageVectors("m", List.of(
                new PassageVectors.Vector(9L, new float[] { 0.5f }, new float[] { 0.25f }),
                new PassageVectors.Vector(10L, new float[] { 0.75f }))));
        PassageVectors back = read(vectors, PassageVectors.class);
        assertThat(back.vectors().get(0).questionVector()).containsExactly(0.25f);
        assertThat(back.vectors().get(1).questionVector()).isNull();
        assertThat(read(MAPPER.writeValueAsString(MAPPER.readTree(vectors).get("vectors").get(0)),
                VectorWithoutQuestion.class).vector()).containsExactly(0.5f);

        StalePassages oldStale = read("""
                {"passages":[{"id":9,"text":"t"}],"remaining":1}""", StalePassages.class);
        assertThat(oldStale.passages().get(0).questionText()).isNull();
        assertThat(oldStale.passages().get(0).ignoreTerms()).isNull();
        String stale = MAPPER.writeValueAsString(new StalePassages.Passage(10L, "q\na", "q", List.of("Cast Farm")));
        assertThat(read(stale, PassageWithoutQuestion.class)).isEqualTo(new PassageWithoutQuestion(10L, "q\na"));

        PatchUpsert oldUpsert = read("""
                {"question":"q","answer":"a","vector":[0.25],"locale":"ar","embeddingModel":"m"}""",
                PatchUpsert.class);
        assertThat(oldUpsert.vector()).containsExactly(0.25f);
        assertThat(oldUpsert.questionVector()).isNull();
        PatchUpsert upsert = roundTrip(new PatchUpsert(null, "q", "a", null, null, new float[] { 0.25f }, "ar", "m",
                9L, true, "h", new float[] { 0.5f }), PatchUpsert.class);
        assertThat(upsert.questionVector()).containsExactly(0.5f);
        assertThat(upsert.contentHash()).isEqualTo("h");

        KnowledgeChunk oldChunk = read("""
                {"chunkText":"q","answer":"a","rowNumber":2,"vector":[0.25],"locale":"ar","embeddingModel":"m"}""",
                KnowledgeChunk.class);
        assertThat(oldChunk.rowNumber()).isEqualTo(2);
        assertThat(oldChunk.questionVector()).isNull();
        KnowledgeChunk chunk = roundTrip(new KnowledgeChunk("q", "a", null, null, 2, new float[] { 0.25f }, "ar", "m",
                new float[] { 0.5f }), KnowledgeChunk.class);
        assertThat(chunk.vector()).containsExactly(0.25f);
        assertThat(chunk.questionVector()).containsExactly(0.5f);

        GapApproveRequest approve = roundTrip(new GapApproveRequest(List.of(1L), "faq", "q", "a", "ar", null, null,
                ASSISTANT).withVectors(new float[] { 0.75f }, new float[] { 0.5f }, "m"), GapApproveRequest.class);
        assertThat(approve.vector()).containsExactly(0.75f);
        assertThat(approve.questionVector()).containsExactly(0.5f);
        assertThat(approve.embeddingModel()).isEqualTo("m");
    }

    /** Ignore terms on the wire: the summary, the settings body and the claim's source view. */
    @Test
    void ignoreTermsSurviveTheWire() throws Exception {
        KnowledgeIndexSummary summary = new KnowledgeIndexSummary(7L, "faq", null, true, "d", 12, 1, 12, 0,
                KnowledgeIndexSummary.STATUS_READY, null, false, List.of("Cast Farm", "كاست فارم"));
        JsonNode node = MAPPER.readTree(MAPPER.writeValueAsString(summary));
        assertThat(node.get("ignoreTerms").get(1).asText()).isEqualTo("كاست فارم");
        assertThat(roundTrip(summary, KnowledgeIndexSummary.class)).isEqualTo(summary);

        IndexSettingsRequest settings = read("{\"ignoreTerms\":[\"CastFarm\"],\"other\":1}",
                IndexSettingsRequest.class);
        assertThat(settings.ignoreTerms()).containsExactly("CastFarm");
        assertThat(read("{}", IndexSettingsRequest.class).ignoreTerms()).isNull();

        KnowledgeSourceView claimed = new KnowledgeSourceView(3L, KnowledgeSourceView.KIND_WEBSITE,
                "https://example.com", "https://example.com", KnowledgeSourceView.STATUS_PROCESSING, null, null, 0, 0,
                0, null, 1, CREATED, CREATED, null).withIgnoreTerms(List.of("Cast Farm"));
        assertThat(roundTrip(claimed, KnowledgeSourceView.class).ignoreTerms()).containsExactly("Cast Farm");
        assertThat(read("""
                {"id":3,"kind":"SHEET","name":"faq.xlsx","status":"READY"}""", KnowledgeSourceView.class).ignoreTerms())
                .isNull();
    }
}
