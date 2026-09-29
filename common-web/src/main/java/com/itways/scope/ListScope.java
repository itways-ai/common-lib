package com.itways.scope;

import java.util.UUID;

/**
 * What a list shows: the whole account, the shared rows only, or one
 * assistant's rows plus the shared ones.
 *
 * <p>
 * Queries take it as two parameters so one statement serves every kind:
 *
 * <pre>
 * (:scopeAll = true OR x.assistantId IS NULL OR x.assistantId = :scopeAssistant)
 * </pre>
 *
 * with {@link #all()} and {@link #assistantOrNone()}. The assistant is never
 * bound as null, because Postgres cannot type a bare null parameter; the nil
 * UUID matches no row instead.
 */
public record ListScope(Kind kind, UUID assistantId) {

    /** The nil UUID: no assistant has it, so comparing against it matches nothing. */
    public static final UUID NONE = new UUID(0L, 0L);

    public enum Kind {
        /** Every row in the account. */
        ALL,
        /** Shared rows only. */
        SHARED,
        /** One assistant's rows plus the shared ones. */
        ASSISTANT
    }

    public static final ListScope ALL = new ListScope(Kind.ALL, null);
    public static final ListScope SHARED = new ListScope(Kind.SHARED, null);

    public ListScope {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if ((kind == Kind.ASSISTANT) != (assistantId != null)) {
            throw new IllegalArgumentException("an assistant id goes with ASSISTANT, and only with it");
        }
    }

    public static ListScope assistant(UUID assistantId) {
        return new ListScope(Kind.ASSISTANT, assistantId);
    }

    /** The list for rows usable in a scope: an assistant's own plus shared, or shared only. */
    public static ListScope usableIn(AssistantScope scope) {
        return scope.shared() ? SHARED : assistant(scope.assistantId());
    }

    /** For the {@code :scopeAll} query parameter. */
    public boolean all() {
        return kind == Kind.ALL;
    }

    /** For the {@code :scopeAssistant} query parameter; never null. */
    public UUID assistantOrNone() {
        return assistantId == null ? NONE : assistantId;
    }

    /** Whether a row stored under {@code rowAssistantId} is in this list. */
    public boolean includes(UUID rowAssistantId) {
        return switch (kind) {
            case ALL -> true;
            case SHARED -> rowAssistantId == null;
            case ASSISTANT -> rowAssistantId == null || rowAssistantId.equals(assistantId);
        };
    }
}
