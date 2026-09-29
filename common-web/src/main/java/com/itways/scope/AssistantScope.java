package com.itways.scope;

import java.util.UUID;

/**
 * Where a row belongs: to one assistant, or shared by every assistant in the
 * account. Stored as a nullable {@code assistant_id}, where null means shared.
 *
 * @param assistantId the owning assistant, or null when shared
 */
public record AssistantScope(UUID assistantId) {

    public static final AssistantScope SHARED = new AssistantScope(null);

    public static AssistantScope of(UUID assistantId) {
        return assistantId == null ? SHARED : new AssistantScope(assistantId);
    }

    public boolean shared() {
        return assistantId == null;
    }

    /**
     * Whether a row stored under {@code rowAssistantId} may be used by something
     * in this scope. An assistant uses its own rows and the shared ones; a shared
     * row (a shared journey, say) runs under any assistant, so it may only use
     * shared rows.
     */
    public boolean mayUse(UUID rowAssistantId) {
        return rowAssistantId == null || rowAssistantId.equals(assistantId);
    }
}
