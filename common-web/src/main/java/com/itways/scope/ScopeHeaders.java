package com.itways.scope;

/** How a request names the assistant it works for. */
public final class ScopeHeaders {

    /**
     * The assistant selected in the console. The portal sends it on every call,
     * so a list defaults to that assistant's rows plus the shared ones, and a new
     * row belongs to that assistant unless the request says otherwise.
     *
     * <p>
     * While callers move over, a request that sends only
     * {@link #LEGACY_ASSISTANT} is read as if it had sent this header (see
     * {@link LegacyAssistantHeaderFilter}), so a service reads this name only.
     */
    public static final String ASSISTANT = "X-Assistant-Id";

    /**
     * The value of {@link #ASSISTANT} (or of {@link #SCOPE_PARAM}) that selects the
     * virtual Shared workspace instead of an assistant (2.4.0): the console is on
     * the shared rows, so lists show shared only and new rows are shared. See
     * {@link SelectedScope}.
     */
    public static final String SHARED_VALUE = "shared";

    /**
     * The name {@link #ASSISTANT} had before 2.1.0, still accepted when a request
     * does not send {@link #ASSISTANT}. Nothing in the platform should send it:
     * it is read only so that a portal or service not yet moved keeps working.
     *
     * @deprecated since 2.1.0; send {@link #ASSISTANT}. Removed, with
     *             {@link LegacyAssistantHeaderFilter}, once every caller sends the
     *             new name.
     */
    @Deprecated(since = "2.1.0", forRemoval = true)
    public static final String LEGACY_ASSISTANT = "X-Nibras-Assistant";

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
