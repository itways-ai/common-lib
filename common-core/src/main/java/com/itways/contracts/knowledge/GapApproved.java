package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What approving a gap group did (2.2.0).
 *
 * @param resolved        how many gaps this call marked resolved
 * @param chunkId         the row that answers them (the existing one on a repeat)
 * @param alreadyResolved true when an earlier call had done it already: nothing was written again
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GapApproved(
        int resolved,
        Long chunkId,
        boolean alreadyResolved) {
}
