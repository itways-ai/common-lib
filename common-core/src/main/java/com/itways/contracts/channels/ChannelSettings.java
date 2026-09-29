package com.itways.contracts.channels;

import java.util.Set;

/**
 * The keys of a channel's {@code channel_settings} JSON, and the values each
 * accepts.
 *
 * <p>
 * channels-service validates and stores them; conversation-service reads them back
 * from the same row to run the channel. A key renamed on one side only used to
 * read as "not set" on the other — no error, just a channel quietly falling back
 * to defaults — which is why neither service declares them itself.
 *
 * <p>
 * Plain constants rather than enums: the values are compared, stored and
 * switched on as strings on both sides, and compile-time constants keep that
 * possible.
 */
public final class ChannelSettings {

    // ─── Twilio (WhatsApp and voice) ───
    public static final String TWILIO_ACCOUNT_SID = "twilioAccountSid";
    /** Stored encrypted; decrypted only when a message or call is sent. */
    public static final String TWILIO_AUTH_TOKEN = "twilioAuthToken";

    // ─── WhatsApp ───
    public static final String WHATSAPP_FROM = "whatsappFrom";

    // ─── Telegram ───
    /** Stored encrypted. */
    public static final String TELEGRAM_BOT_TOKEN = "botToken";
    public static final String TELEGRAM_WEBHOOK_SECRET = "webhookSecretToken";

    // ─── Voice ───
    public static final String VOICE_NUMBER = "voiceNumber";
    public static final String TTS_VOICE_AR = "ttsVoiceAr";
    public static final String TTS_VOICE_EN = "ttsVoiceEn";
    public static final String MAX_CALL_MINUTES = "maxCallMinutes";
    /** One of {@link Transport}. */
    public static final String TRANSPORT = "transport";
    /** Where a caller is transferred when a journey hands off to a person. */
    public static final String HANDOFF_NUMBER = "handoffNumber";
    /** One of {@link OtpDelivery}. */
    public static final String OTP_DELIVERY = "otpDelivery";
    /** One of {@link IdentityMode}. */
    public static final String IDENTITY_MODE = "identityMode";
    public static final String IDENTITY_TOKEN_ENDPOINT = "identityTokenEndpoint";
    /** 0 or absent means no monthly cap. */
    public static final String MAX_MONTHLY_MINUTES = "maxMonthlyMinutes";
    public static final String GUEST_JOURNEY_ID = "guestJourneyId";
    /** One of {@link GuestMode}. */
    public static final String GUEST_MODE = "guestMode";

    public static final int DEFAULT_MAX_CALL_MINUTES = 15;

    /** How a call's audio reaches the assistant. */
    public static final class Transport {
        /** Twilio recognises speech and posts text; the default. */
        public static final String GATHER = "GATHER";
        /** Twilio ConversationRelay over a WebSocket. */
        public static final String RELAY = "RELAY";
        /** Raw audio both ways; the platform runs recognition and synthesis itself. */
        public static final String STREAM = "STREAM";
        public static final Set<String> ALL = Set.of(GATHER, RELAY, STREAM);

        private Transport() {
        }
    }

    /** How a caller's one-time code is sent. */
    public static final class OtpDelivery {
        public static final String SMS = "SMS";
        public static final String WHATSAPP = "WHATSAPP";
        public static final Set<String> ALL = Set.of(SMS, WHATSAPP);

        private OtpDelivery() {
        }
    }

    /** Whether callers are asked to prove who they are. */
    public static final class IdentityMode {
        public static final String PROMPT = "PROMPT";
        public static final String OFF = "OFF";
        public static final Set<String> ALL = Set.of(PROMPT, OFF);

        private IdentityMode() {
        }
    }

    /** What an unverified caller may do. */
    public static final class GuestMode {
        public static final String AGENT = "AGENT";
        public static final String JOURNEY = "JOURNEY";
        public static final String FREE = "FREE";
        public static final Set<String> ALL = Set.of(AGENT, JOURNEY, FREE);

        private GuestMode() {
        }
    }

    private ChannelSettings() {
    }
}
