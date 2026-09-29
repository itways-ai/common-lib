package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * How many passages removing a source deleted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourceRemoval(
        int removed) {
}
