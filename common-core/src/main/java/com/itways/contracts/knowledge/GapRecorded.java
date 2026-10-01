package com.itways.contracts.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The id of a recorded gap.
 *
 * @param merged true when the report was merged into an existing open gap (which then counts one
 *               more ask) instead of recorded as a new one; {@code id} is that gap's (2.2.0)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GapRecorded(
        long id,
        boolean merged) {

    /** The 2.1.0 shape: a new gap. */
    public GapRecorded(long id) {
        this(id, false);
    }
}
