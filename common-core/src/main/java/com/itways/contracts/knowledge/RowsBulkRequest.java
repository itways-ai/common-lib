package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One action on many rows of an index (2.2.0; {@code POST /{index}/rows/bulk}).
 *
 * @param action {@link #ACTION_DELETE}, {@link #ACTION_ENABLE} or {@link #ACTION_DISABLE}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RowsBulkRequest(
        List<Long> ids,
        String action) {

    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_ENABLE = "ENABLE";
    /** Keeps the rows but never serves them. */
    public static final String ACTION_DISABLE = "DISABLE";
}
