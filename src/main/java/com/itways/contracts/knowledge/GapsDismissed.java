package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * How many gaps were set aside.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GapsDismissed(
        int dismissed) {
}
