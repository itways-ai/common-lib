package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An ingestion worker asks for work (2.2.0; journey-service {@code POST /ingestion/claim}):
 * up to {@code limit} queued sources, or processing ones whose lease ran out, each leased to
 * {@code worker} for {@code leaseSeconds}.
 *
 * @param worker which worker claims, e.g. {@code host:pid}; kept on the source while it runs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimRequest(
        String worker,
        int leaseSeconds,
        int limit) {
}
