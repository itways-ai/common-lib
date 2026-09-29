package com.itways.contracts.channels;

/**
 * The kinds of channel. Stored by name in {@code channels.type}; channels-service
 * writes it and conversation-service and journey-service read it.
 */
public enum ChannelType {
	WHATSAPP_TWILIO,
	TELEGRAM,
	/**
	 * Assistant embedded in a customer's own web application via the Web SDK.
	 *
	 * <p>
	 * Unlike the messaging channels, WEB has no webhook: the browser calls the
	 * platform directly, authenticated by a public origin-bound channel key, and
	 * may forward the signed-in end user's bearer token so journeys can call that
	 * application's APIs as the user.
	 */
	WEB,
	/**
	 * Inbound phone calls on a Twilio voice number.
	 *
	 * <p>
	 * Twilio turns the caller's speech into text and posts it to the voice
	 * webhook exactly as it posts a WhatsApp message; the platform answers with
	 * TwiML (or, on the real-time transport, WebSocket text) that Twilio speaks
	 * back. The number is wired to the platform through Twilio's REST API when
	 * the channel is created — there is nothing to paste into the console.
	 */
	VOICE_TWILIO
}
