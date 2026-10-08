package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * How many gaps were marked answered.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GapsResolved(
        int resolved) {
}
