package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Unanswered questions that mean the same thing, grouped for review.
 *
 * @param count    how many times it was asked
 * @param examples a few of the wordings used
 * @param gapIds   every gap in the group, for resolve and dismiss
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
        LocalDateTime lastAsked) {
}
