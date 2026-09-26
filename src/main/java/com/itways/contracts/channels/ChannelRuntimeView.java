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
 * (speech-service) or check what is reachable (journey-service).
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
	/** Owning assistant — the tamper-proof source of a conversation's scope; null means the account default. */
	private UUID assistantId;
	private String username;
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
