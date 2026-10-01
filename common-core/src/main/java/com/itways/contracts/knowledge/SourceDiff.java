package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What replacing a source's passages changed, compared by content hash (2.2.0).
 *
 * @param inserted new passages (they wait for their embedding)
 * @param kept     passages that were already there, with their vectors
 * @param deleted  passages that are gone from the source
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourceDiff(
        int inserted,
        int kept,
        int deleted) {
}
