package com.itways.contracts.knowledge;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Gaps to resolve or dismiss.
 *
 * @param resolvedIndex the index whose new row now answers them; null when dismissing
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GapMarkRequest(
        List<Long> gapIds,
        String resolvedIndex) {
}
