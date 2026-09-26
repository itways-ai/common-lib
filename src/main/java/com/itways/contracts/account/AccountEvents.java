package com.itways.contracts.account;

/**
 * What account-service announces on RabbitMQ when an account's configuration
 * changes, so other services can react instead of polling or reading its
 * tables.
 *
 * <p>
 * One durable topic exchange, {@value #EXCHANGE}; the routing key names the
 * change. Messages are JSON: {@link AiConfigChanged} for
 * {@value #AI_CONFIG_CHANGED}, {@link AssistantChanged} for every
 * {@code assistant.*} key. A consumer binds its own queue with the keys it
 * cares about ({@code assistant.*} for all assistant changes). Delivery is
 * best-effort, after the change has committed: treat an event as a hint to
 * refresh, not as the only way to learn about a change.
 */
public final class AccountEvents {

    public static final String EXCHANGE = "account.events";

    /** An account's AI provider configs changed; copies of the decrypted default are stale. */
    public static final String AI_CONFIG_CHANGED = "ai-config.changed";

    public static final String ASSISTANT_CREATED = "assistant.created";
    public static final String ASSISTANT_UPDATED = "assistant.updated";
    /** Set to DISABLED: it keeps its journeys and channels but must answer nothing. */
    public static final String ASSISTANT_DISABLED = "assistant.disabled";
    public static final String ASSISTANT_ENABLED = "assistant.enabled";
    /** Gone: journeys and channels that still point at it are orphaned. */
    public static final String ASSISTANT_DELETED = "assistant.deleted";

    private AccountEvents() {
    }
}
