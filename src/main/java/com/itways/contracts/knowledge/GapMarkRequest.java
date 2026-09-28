package com.itways.contracts.knowledge;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Gaps to resolve or dismiss.
 *
 * @param resolvedIndex the index whose new row now answers them; null when dismissing
 * @param assistantId   the assistant whose gaps they are; required. Only that assistant's gaps
 *                      are closed, whatever ids the request lists
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GapMarkRequest(
        List<Long> gapIds,
        String resolvedIndex,
        UUID assistantId) {

    /** Without the assistant: refused by journey-service, which needs to know whose gaps these are. */
    public GapMarkRequest(List<Long> gapIds, String resolvedIndex) {
        this(gapIds, resolvedIndex, null);
    }
}
