package com.itways.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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

    public static final String QUEUE_NAME = "account.activity.queue";
    public static final String EXCHANGE_NAME = "account.activity.exchange";
    public static final String ROUTING_KEY = "account.activity.record";
}
