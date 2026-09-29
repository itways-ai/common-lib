package com.itways.contracts.channels;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A phone number linked to one of a client's users, as channels-service serves
 * it on {@code /api/channels/internal/identities} (CHN-09).
 *
 * <p>
 * channels-service is the only writer of {@code channel_identities}:
 * conversation-service asks it to look a caller up, to record that a verified caller
 * used their link, and to link a number verified from the web widget, instead
 * of mapping and writing the table itself.
 */
public record ChannelIdentityView(UUID id, String accountId, String phone, String externalUserId,
        String displayName, LocalDateTime verifiedAt, LocalDateTime lastUsedAt) {

    /** Body of {@code POST /api/channels/internal/identities/link}. */
    public record LinkRequest(String phone, String externalUserId, String displayName) {
    }

    /** Body of {@code POST /api/channels/internal/identities/used}. */
    public record UsedRequest(String phone) {
    }
}
