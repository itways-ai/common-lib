package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The id of a recorded gap.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GapRecorded(
        long id) {
}
