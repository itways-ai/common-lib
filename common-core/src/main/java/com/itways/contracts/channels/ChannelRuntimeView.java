package com.itways.contracts.channels;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A channel as the runtime sees it, served by channels-service at
 * {@code GET /api/channels/internal/{id}} to the services that answer on it
 * (conversation-service) or check what is reachable (journey-service).
 *
 * <p>
 * channels-service owns the {@code channels} table; nobody else maps or
 * queries it (CH-14). Secrets in {@link #channelSettings} are as stored —
 * encrypted with the platform encryption key — and are decrypted only by the
 * service that sends a message with them.
 *
 * <p>
 * {@code type} and {@code status} are the enum names ({@link ChannelType},
 * {@link ChannelStatus}) as strings, so a new value does not break a reader
 * built before it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelRuntimeView {

    private UUID id;
    private String accountId;
    /**
     * The assistant the channel answers as: the tamper-proof source of a
     * conversation's scope. Every channel has exactly one and it is never
     * shared. Null only on a channel older than that rule whose account had no
     * default assistant to give it; the runtime answers on such a channel as the
     * account's default assistant if there is one by now, and not at all
     * otherwise. A disabled or deleted assistant's channel does not answer.
     */
    private UUID assistantId;
    private String label;
    private String type;
    private String status;
    private Long defaultJourneyId;
    private boolean executionSessionEnabled;
    private Map<String, Object> channelSettings;
    /** Raised each time the channel's webhook URL is rotated; older URLs are refused. */
    private int webhookVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
