package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * How many rows a bulk action changed (2.2.0); rows of other indexes or accounts are never
 * counted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RowsBulkResult(
        int affected) {
}
