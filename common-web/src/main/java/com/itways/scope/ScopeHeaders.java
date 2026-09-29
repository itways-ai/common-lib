package com.itways.scope;

/** How a request names the assistant it works for. */
public final class ScopeHeaders {

    /**
     * The assistant selected in the console. The portal sends it on every call,
     * so a list defaults to that assistant's rows plus the shared ones, and a new
     * row belongs to that assistant unless the request says otherwise.
     */
    public static final String ASSISTANT = "X-Nibras-Assistant";

    /**
     * Query parameter that overrides {@link #ASSISTANT} for one list:
     * {@code all}, {@code shared}, or an assistant id. Pickers use it to list
     * what fits the thing being edited (a shared journey may only use shared
     * templates) rather than what the console has selected.
     */
    public static final String SCOPE_PARAM = "scope";

    private ScopeHeaders() {
    }
}
