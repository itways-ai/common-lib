package com.itways.scope;

import java.util.UUID;

/**
 * What the console has selected, as sent in {@link ScopeHeaders#ASSISTANT}: one
 * assistant, the virtual <em>Shared</em> workspace
 * ({@link ScopeHeaders#SHARED_VALUE}), or nothing.
 *
 * <p>
 * The Shared workspace is not a row anywhere: it is a name for the existing
 * shared scope (rows with a null {@code assistant_id}). Selecting it makes a
 * list show the shared rows only ({@link ListScope#SHARED}) and makes a new
 * row shared ({@link AssistantScope#SHARED}) unless the request names an
 * assistant. Kinds of row that can never be shared (channels) refuse it with
 * {@code SCOPE_REQUIRED}: the caller must pick an owner.
 *
 * @param kind        what is selected
 * @param assistantId the assistant, only with {@link Kind#ASSISTANT}
 */
public record SelectedScope(Kind kind, UUID assistantId) {

    public enum Kind {
        /** One assistant. */
        ASSISTANT,
        /** The virtual Shared workspace. */
        SHARED,
        /** The header was not sent. */
        NONE
    }

    public static final SelectedScope SHARED = new SelectedScope(Kind.SHARED, null);
    public static final SelectedScope NONE = new SelectedScope(Kind.NONE, null);

    public SelectedScope {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if ((kind == Kind.ASSISTANT) != (assistantId != null)) {
            throw new IllegalArgumentException("an assistant id goes with ASSISTANT, and only with it");
        }
    }

    /** One assistant, or {@link #NONE} for null (what a {@code UUID} header used to mean). */
    public static SelectedScope of(UUID assistantId) {
        return assistantId == null ? NONE : new SelectedScope(Kind.ASSISTANT, assistantId);
    }

    public static SelectedScope assistant(UUID assistantId) {
        return new SelectedScope(Kind.ASSISTANT, assistantId);
    }

    /**
     * The header's value: null or blank is {@link #NONE}, {@code shared} (any
     * case, trimmed) is {@link #SHARED}, anything else must be an assistant id.
     *
     * @throws com.itways.common.exception.BusinessException 400 {@code INVALID_SCOPE}
     *                                                       when it is neither
     */
    public static SelectedScope parse(String header) {
        if (header == null || header.isBlank()) {
            return NONE;
        }
        String value = header.trim();
        if (ScopeHeaders.SHARED_VALUE.equalsIgnoreCase(value)) {
            return SHARED;
        }
        try {
            return assistant(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw ScopeErrors.invalidScope(value);
        }
    }

    public boolean shared() {
        return kind == Kind.SHARED;
    }

    public boolean none() {
        return kind == Kind.NONE;
    }

    /** The list this selection shows by default: own plus shared, shared only, or the whole account. */
    public ListScope asListScope() {
        return switch (kind) {
            case ASSISTANT -> ListScope.assistant(assistantId);
            case SHARED -> ListScope.SHARED;
            case NONE -> ListScope.ALL;
        };
    }
}
