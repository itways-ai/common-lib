package com.itways.contracts.account;

import java.util.Map;

/**
 * What one service still keeps under an assistant, by kind: for example
 * {@code {"journeys": 2, "knowledgeIndexes": 1}}. Answered by each service that
 * stores per-assistant rows at {@code GET /api/<service>/internal/assistants/{id}/usage},
 * and read by account-service before it deletes an assistant: an assistant that
 * still owns anything is not deleted, so nothing it owned is orphaned or leaks
 * into another assistant.
 *
 * @param owned count per kind; kinds with nothing are omitted or zero
 */
public record AssistantUsage(Map<String, Long> owned) {

    public long total() {
        return owned == null ? 0 : owned.values().stream().mapToLong(Long::longValue).sum();
    }
}
