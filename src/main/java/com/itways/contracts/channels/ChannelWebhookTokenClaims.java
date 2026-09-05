package com.itways.contracts.channels;

/**
 * The claims of a channel webhook token.
 *
 * <p>
 * channels-service mints the token when a Telegram or WhatsApp channel is
 * registered; assistant-service verifies it on every inbound webhook. The two
 * must agree on every claim name, which is why this lives in neither service.
 */
public final class ChannelWebhookTokenClaims {

	public static final String TYPE_CHANNEL_WEBHOOK = "CHANNEL_WEBHOOK";

	public static final String CLAIM_TYPE = "type";
	public static final String CLAIM_ACC_H = "accH";
	public static final String CLAIM_ACC_E = "accE";
	public static final String CLAIM_CHANNEL_ID = "channelId";

	private ChannelWebhookTokenClaims() {
	}
}
