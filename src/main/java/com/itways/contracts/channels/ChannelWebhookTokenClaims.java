package com.itways.contracts.channels;

/**
 * The claims of a channel webhook token.
 *
 * <p>
 * channels-service mints the token when a Telegram, WhatsApp or voice channel
 * is registered; speech-service verifies it on every inbound webhook. The two
 * must agree on every claim name, which is why this lives in neither service.
 */
public final class ChannelWebhookTokenClaims {

	public static final String TYPE_CHANNEL_WEBHOOK = "CHANNEL_WEBHOOK";

	public static final String CLAIM_TYPE = "type";
	public static final String CLAIM_ACC_H = "accH";
	public static final String CLAIM_ACC_E = "accE";
	public static final String CLAIM_CHANNEL_ID = "channelId";
	/**
	 * The channel's webhook version when the token was minted. Rotating a
	 * channel's URL raises the version, which retires every older token.
	 * Absent on tokens minted before rotation existed; read as 1.
	 */
	public static final String CLAIM_VERSION = "ver";

	/** The subject is the channel, never the user who set it up: {@code channel:<id>}. */
	public static final String SUBJECT_PREFIX = "channel:";

	/**
	 * The {@code kid} header of a token signed with the dedicated webhook key
	 * ({@code CHANNEL_WEBHOOK_PRIVATE_KEY}). A token with this header is only
	 * ever a webhook credential; a token without it is verified with the
	 * platform key.
	 */
	public static final String KEY_ID = "channel-webhook";

	private ChannelWebhookTokenClaims() {
	}
}
