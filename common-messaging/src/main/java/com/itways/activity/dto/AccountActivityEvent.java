package com.itways.activity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An account activity entry, sent to account-service over
 * {@value #EXCHANGE_NAME}.
 *
 * <p>
 * {@code requestId} (ARC-25) is the id of the request that caused the event;
 * the outbox fills it from the logging context when the caller did not, and
 * the message carries it as the {@code x-request-id} header. It is left out of
 * the JSON when not set, so events and stored outbox rows without one keep
 * their former shape.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountActivityEvent {

    private UUID eventId;
    private String accountId;
    private ActivityCategory category;
    private String action;
    private String title;
    private String resourceType;
    private String resourceId;
    private Map<String, Object> metadata;
    private Instant occurredAt;
    private String ipAddress;
    private String userAgent;
    private ActivitySeverity severity;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String requestId;

    public static final String QUEUE_NAME = "account.activity.queue";
    public static final String EXCHANGE_NAME = "account.activity.exchange";
    public static final String ROUTING_KEY = "account.activity.record";

    /** Every field but {@code requestId} (the constructor from before ARC-25). */
    public AccountActivityEvent(UUID eventId, String accountId, ActivityCategory category, String action, String title,
            String resourceType, String resourceId, Map<String, Object> metadata, Instant occurredAt,
            String ipAddress, String userAgent, ActivitySeverity severity) {
        this(eventId, accountId, category, action, title, resourceType, resourceId, metadata, occurredAt, ipAddress,
                userAgent, severity, null);
    }
}
