package com.itways.security.core;

import java.security.Key;
import java.security.PublicKey;
import java.time.Clock;
import java.util.Date;

import com.itways.contracts.channels.ChannelWebhookTokenClaims;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.SignatureException;

/**
 * Verifies the platform's JWTs: user access and refresh tokens (auth-service)
 * and channel webhook tokens (channels-service). The one implementation of
 * these rules; {@code JwtTokenProvider} and the api-gateway both use it.
 *
 * <p>
 * The key is chosen by the {@code kid} header:
 * <ul>
 * <li>{@code channel-webhook}: the dedicated webhook key
 * ({@code CHANNEL_WEBHOOK_PUBLIC_KEY}). While a rotation is under way, a token
 * that does not verify with it is tried with the previous one
 * ({@code CHANNEL_WEBHOOK_PUBLIC_KEY_PREVIOUS}), so the URLs already registered
 * with Telegram and Twilio keep working until each channel's URL is
 * rotated.</li>
 * <li>anything else, including no kid (user access and refresh tokens carry
 * none): the platform key ({@code RSA_PUBLIC_KEY}).</li>
 * </ul>
 * Two rules keep the keys apart: a token signed with the webhook key is only
 * ever a webhook credential, and once the webhook key is configured a webhook
 * credential signed with the platform key is refused. The token must not have
 * expired.
 *
 * <p>
 * What a verified token is, by its {@code type} claim: none is a user
 * {@link Kind#ACCESS} token, {@code REFRESH} a {@link Kind#REFRESH} token (good
 * only for minting a new access token), {@code CHANNEL_WEBHOOK} a
 * {@link Kind#CHANNEL_WEBHOOK} token. What each kind may do is the caller's
 * decision.
 *
 * <p>
 * Immutable and thread-safe. No framework, no logging.
 */
public final class TokenVerifier {

    /** Claim naming what a token is for; absent on a user access token. */
    public static final String CLAIM_TYPE = ChannelWebhookTokenClaims.CLAIM_TYPE;

    /** What a token with no {@link #CLAIM_TYPE} is: an ordinary user access token. */
    public static final String TYPE_ACCESS = "ACCESS";

    /** A refresh token: good only for minting a new access token, never for an API call. */
    public static final String TYPE_REFRESH = "REFRESH";

    /** A channel webhook token, carried in a provider's webhook URL. */
    public static final String TYPE_CHANNEL_WEBHOOK = ChannelWebhookTokenClaims.TYPE_CHANNEL_WEBHOOK;

    /** The {@code kid} of a token signed with the dedicated webhook key. */
    public static final String WEBHOOK_KEY_ID = ChannelWebhookTokenClaims.KEY_ID;

    /** What a token is. */
    public enum Kind {
        /** No {@code type} claim (or {@code ACCESS}): a user access token. */
        ACCESS,
        REFRESH,
        CHANNEL_WEBHOOK,
        /** Verified, but with a {@code type} this library does not know. */
        OTHER,
        /** Did not verify; {@link Result#failure()} says why. */
        INVALID
    }

    /** Why a token did not verify. */
    public enum Failure {
        /** Malformed, badly signed, signed with the wrong key for its kind, or an unreadable type. */
        INVALID,
        EXPIRED,
        /** The key this token needs is not configured here. */
        KEY_NOT_CONFIGURED
    }

    /**
     * The outcome of {@link #check}: the claims and kind of a verified token,
     * or {@link Kind#INVALID} with the {@link Failure}.
     */
    public record Result(Kind kind, Claims claims, Failure failure) {

        static Result verified(Claims claims) {
            return new Result(kindOf(claims.get(CLAIM_TYPE, String.class)), claims, null);
        }

        static Result failed(Failure failure) {
            return new Result(Kind.INVALID, null, failure);
        }

        public boolean isVerified() {
            return kind != Kind.INVALID;
        }

        /** The raw {@code type} claim, {@code null} on an access token or when not verified. */
        public String type() {
            return claims == null ? null : claims.get(CLAIM_TYPE, String.class);
        }
    }

    /**
     * Thrown from the key locator when the key a token needs is missing. A
     * {@link JwtException}, so it leaves the parser as it is.
     */
    public static final class KeyNotConfiguredException extends JwtException {

        public KeyNotConfiguredException(String message) {
            super(message);
        }
    }

    private final PublicKey platformKey;
    private final PublicKey webhookKey;
    private final PublicKey webhookPreviousKey;
    private final Clock clock;

    /**
     * @param platformKey        the key of access and refresh tokens; {@code null}
     *                           when not configured (such tokens are then
     *                           refused as {@link Failure#KEY_NOT_CONFIGURED})
     * @param webhookKey         the channel webhook key, or {@code null}: webhook
     *                           tokens are then expected to be signed with the
     *                           platform key, as before the key split
     * @param webhookPreviousKey the webhook key being retired, or {@code null}
     * @param clock              the clock expiry is judged by; {@code null} for
     *                           jjwt's own system clock
     * @throws IllegalStateException when a previous webhook key is given without
     *                               a current one
     */
    public TokenVerifier(PublicKey platformKey, PublicKey webhookKey, PublicKey webhookPreviousKey, Clock clock) {
        if (webhookPreviousKey != null && webhookKey == null) {
            throw new IllegalStateException(
                    "CHANNEL_WEBHOOK_PUBLIC_KEY_PREVIOUS is set but CHANNEL_WEBHOOK_PUBLIC_KEY is not");
        }
        this.platformKey = platformKey;
        this.webhookKey = webhookKey;
        this.webhookPreviousKey = webhookPreviousKey;
        this.clock = clock;
    }

    public boolean hasPlatformKey() {
        return platformKey != null;
    }

    public boolean hasWebhookKey() {
        return webhookKey != null;
    }

    public boolean hasWebhookPreviousKey() {
        return webhookPreviousKey != null;
    }

    /**
     * The verified claims of {@code token}.
     *
     * @throws JwtException             when the signature, expiry or key rules
     *                                  fail ({@link ExpiredJwtException} when
     *                                  expired, {@link KeyNotConfiguredException}
     *                                  when the key it needs is missing)
     * @throws IllegalArgumentException when the token is null or empty
     */
    public Claims verify(String token) {
        boolean[] webhookKeyUsed = { false };
        Claims claims;
        try {
            claims = parse(token, webhookKey, webhookKeyUsed);
        } catch (SignatureException e) {
            // During a webhook key rotation, URLs signed with the old key are
            // still registered with the providers.
            if (!webhookKeyUsed[0] || webhookPreviousKey == null) {
                throw e;
            }
            claims = parse(token, webhookPreviousKey, webhookKeyUsed);
        }
        boolean webhookType = TYPE_CHANNEL_WEBHOOK.equals(claims.get(CLAIM_TYPE, String.class));
        if (webhookKeyUsed[0] && !webhookType) {
            throw new JwtException("Only a channel webhook token may be signed with the webhook key");
        }
        if (webhookType && webhookKey != null && !webhookKeyUsed[0]) {
            throw new JwtException("Channel webhook tokens must be signed with the webhook key");
        }
        return claims;
    }

    /** {@link #verify}, without exceptions: for callers that decide on the outcome (the gateway). */
    public Result check(String token) {
        try {
            return Result.verified(verify(token));
        } catch (ExpiredJwtException e) {
            return Result.failed(Failure.EXPIRED);
        } catch (KeyNotConfiguredException e) {
            return Result.failed(Failure.KEY_NOT_CONFIGURED);
        } catch (JwtException | IllegalArgumentException e) {
            return Result.failed(Failure.INVALID);
        }
    }

    /** The kind a verified token's {@code type} claim names. */
    public static Kind kindOf(String type) {
        if (type == null || TYPE_ACCESS.equals(type)) {
            return Kind.ACCESS;
        }
        if (TYPE_REFRESH.equals(type)) {
            return Kind.REFRESH;
        }
        if (TYPE_CHANNEL_WEBHOOK.equals(type)) {
            return Kind.CHANNEL_WEBHOOK;
        }
        return Kind.OTHER;
    }

    private Claims parse(String token, PublicKey currentWebhookKey, boolean[] webhookKeyUsed) {
        JwtParserBuilder parser = Jwts.parser();
        if (clock != null) {
            parser.clock(() -> Date.from(clock.instant()));
        }
        return parser
                .keyLocator(new LocatorAdapter<Key>() {
                    @Override
                    protected Key locate(ProtectedHeader header) {
                        if (WEBHOOK_KEY_ID.equals(header.getKeyId())) {
                            if (currentWebhookKey == null) {
                                throw new KeyNotConfiguredException(
                                        "Token signed with the webhook key, which is not configured here");
                            }
                            webhookKeyUsed[0] = true;
                            return currentWebhookKey;
                        }
                        if (platformKey == null) {
                            throw new KeyNotConfiguredException("The platform token key is not configured here");
                        }
                        return platformKey;
                    }
                })
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
