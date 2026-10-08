package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Unanswered questions that mean the same thing, grouped for review.
 *
 * @param count      how many gaps the group holds
 * @param examples   a few of the wordings used
 * @param gapIds     every gap in the group, for resolve and dismiss
 * @param hitCount   how many times it was asked: the gaps' ask counts added up, at least
 *                   {@code count} (2.2.0; a gap counts every near-identical ask merged into it)
 * @param indexNames the indexes that were searched when it was asked (2.2.0)
 * @param source     {@code GapReport.SOURCE_KNOWLEDGE_STEP} or {@code GapReport.SOURCE_FALLBACK}
 *                   when every gap of the group has the same one; null otherwise (2.2.0)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GapGroup(
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String question,
        int count,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> languages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> channels,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> examples,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> gapIds,
        LocalDateTime firstAsked,
        LocalDateTime lastAsked,
        int hitCount,
        List<String> indexNames,
        String source) {

    /** The 2.1.0 shape: asked once per gap ({@code hitCount = count}), no index names, no source. */
    public GapGroup(long id, String question, int count, List<String> languages, List<String> channels,
            List<String> examples, List<Long> gapIds, LocalDateTime firstAsked, LocalDateTime lastAsked) {
        this(id, question, count, languages, channels, examples, gapIds, firstAsked, lastAsked, count, List.of(),
                null);
    }
}
