package com.itways.security.jwt;

import com.itways.security.core.PublicKeys;
import com.itways.security.core.TokenVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("jwtTokenProvider")
@Slf4j
public class JwtTokenProvider {

    /** The variable that holds the platform's token verification key. */
    public static final String PUBLIC_KEY_ENV = "RSA_PUBLIC_KEY";
    static final String PRIVATE_KEY_ENV = "RSA_PRIVATE_KEY";
    static final String WEBHOOK_PUBLIC_KEY_ENV = "CHANNEL_WEBHOOK_PUBLIC_KEY";
    static final String WEBHOOK_PREVIOUS_PUBLIC_KEY_ENV = "CHANNEL_WEBHOOK_PUBLIC_KEY_PREVIOUS";

    /** Only auth-service signs; everywhere else this is empty. */
    @Value("${jwt.rsa.private-key:}")
    private String privateKeyStr;

    /** Claim naming what a token is for; absent on a user access token. */
    public static final String CLAIM_TYPE = TokenVerifier.CLAIM_TYPE;

    /** A refresh token: good only for minting a new access token, never for an API call. */
    public static final String TYPE_REFRESH = TokenVerifier.TYPE_REFRESH;

    /** What a token with no {@link #CLAIM_TYPE} is: an ordinary user access token. */
    public static final String TYPE_ACCESS = TokenVerifier.TYPE_ACCESS;

    /**
     * The sign-in session a token belongs to. auth-service puts it in refresh
     * tokens and, since PLT-05, in user access tokens, so signing out ends the
     * session's access tokens too ({@code SessionRevocationStore}). Access
     * tokens minted before that carry none.
     */
    public static final String CLAIM_SESSION_ID = "sid";

    /** Required: the platform's token verification key. */
    @Value("${jwt.rsa.public-key:${RSA_PUBLIC_KEY:}}")
    private String publicKeyStr;

    /**
     * The public half of the key channels-service signs webhook URLs with
     * ({@code CHANNEL_WEBHOOK_PUBLIC_KEY}). Optional: without it a webhook
     * token is verified with the platform key, as before the split.
     */
    @Value("${jwt.channel-webhook.public-key:${CHANNEL_WEBHOOK_PUBLIC_KEY:}}")
    private String webhookPublicKeyStr;

    /**
     * The webhook key being retired during a rotation
     * ({@code CHANNEL_WEBHOOK_PUBLIC_KEY_PREVIOUS}, optional). A webhook token
     * that does not verify with the current key is tried with this one, so the
     * URLs already registered with Telegram and Twilio keep working until each
     * channel's URL is rotated. Remove it once they all are (CHN-12).
     */
    @Value("${jwt.channel-webhook.previous-public-key:${CHANNEL_WEBHOOK_PUBLIC_KEY_PREVIOUS:}}")
    private String webhookPreviousPublicKeyStr;

    @Value("${jwt.access-expiration:3600000}") // 1 hour
    private long accessExpiration;

    @Value("${jwt.refresh-expiration:604800000}") // 7 days
    private long refreshExpiration;

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private PublicKey webhookPublicKey;
    private PublicKey webhookPreviousPublicKey;
    private TokenVerifier verifier;

    /**
     * Loads the keys and fails the startup when they are missing or broken.
     *
     * <p>
     * This bean exists only in services that verify JWTs (those that use
     * {@code @EnableCustomSecurity}), and such a service cannot do its job
     * without the platform's public key: it used to generate a throwaway key
     * pair instead, so it started, looked healthy and refused every real
     * token, including the channel webhook tokens (CH-15). Now it refuses to
     * start and names the variable to set. A service without this bean
     * (notification-service) needs none of these keys.
     *
     * <p>
     * {@code CHANNEL_WEBHOOK_PUBLIC_KEY} stays optional: without it a webhook
     * token is verified with the platform key, which is what channels-service
     * signs with when it has no dedicated webhook key. A value that is set but
     * is not a key fails the startup like a broken {@code RSA_PUBLIC_KEY}.
     */
    @PostConstruct
    public void init() {
        this.publicKey = PublicKeys.fromConfig(PUBLIC_KEY_ENV, publicKeyStr);
        if (this.publicKey == null) {
            throw new IllegalStateException(PUBLIC_KEY_ENV + " (jwt.rsa.public-key) is not configured. This service"
                    + " verifies JWTs and cannot start without the platform's RSA public key: set "
                    + PUBLIC_KEY_ENV + " to the Base64 X.509 public key that auth-service's tokens are signed for"
                    + " (see .env.example).");
        }
        // Verify-only is a first-class mode: only auth-service signs tokens, so
        // every other service is configured with just the public key and never
        // sees the private key.
        this.privateKey = loadPrivateKey(privateKeyStr);

        this.webhookPublicKey = PublicKeys.fromConfig(WEBHOOK_PUBLIC_KEY_ENV, webhookPublicKeyStr);
        this.webhookPreviousPublicKey = PublicKeys.fromConfig(WEBHOOK_PREVIOUS_PUBLIC_KEY_ENV,
                webhookPreviousPublicKeyStr);
        if (webhookPreviousPublicKey != null && webhookPublicKey == null) {
            throw new IllegalStateException(
                    WEBHOOK_PREVIOUS_PUBLIC_KEY_ENV + " is set but " + WEBHOOK_PUBLIC_KEY_ENV + " is not");
        }
        if (webhookPublicKey == null) {
            log.info("{} is not set: channel webhook tokens are verified with the platform key ({})",
                    WEBHOOK_PUBLIC_KEY_ENV, PUBLIC_KEY_ENV);
        }
        // jjwt's own clock, as before the rules moved to TokenVerifier.
        this.verifier = new TokenVerifier(publicKey, webhookPublicKey, webhookPreviousPublicKey, null);
    }

    /**
     * The verified claims of {@code token}.
     *
     * <p>
     * The key is chosen by the {@code kid} header: {@code channel-webhook}
     * means the dedicated webhook key, anything else (including no kid — user
     * access and refresh tokens carry none) the platform key. Two rules keep
     * the keys apart: a token signed with the webhook key is only ever a
     * webhook credential, and once the webhook key is configured a webhook
     * credential signed with the platform key is refused. The rules are
     * {@link TokenVerifier}'s, shared with the api-gateway.
     *
     * @throws io.jsonwebtoken.JwtException when the signature, expiry or key rules fail
     */
    public Claims getVerifiedClaims(String token) {
        return verifier.verify(token);
    }

    private PrivateKey requireSigningKey() {
        if (privateKey == null) {
            throw new IllegalStateException(
                    "No jwt.rsa.private-key configured — this service verifies tokens but cannot mint them");
        }
        return privateKey;
    }

    /** {@code null} when not configured; a set value that is not a key fails, naming the variable. */
    private static PrivateKey loadPrivateKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(key.replaceAll("\\s", ""));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            // The exception class only: a message could quote key material.
            throw new IllegalStateException(PRIVATE_KEY_ENV + " (jwt.rsa.private-key) is set but is not a Base64"
                    + " PKCS#8 RSA private key (" + e.getClass().getSimpleName() + ")");
        }
    }

    public String generateToken(String username, String role) {
        return generateAccessToken(username, Map.of("role", role));
    }

    public String generateAccessToken(String username, Map<String, Object> extraClaims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessExpiration);

        return Jwts.builder()
                .subject(username)
                .claims(extraClaims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(requireSigningKey(), Jwts.SIG.RS256)
                .compact();
    }

    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(requireSigningKey(), Jwts.SIG.RS256)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return getVerifiedClaims(token)
                .getSubject();
    }

    public String getAccountIdHashedFromToken(String token) {
        return getVerifiedClaims(token)
                .get("accH", String.class);
    }

    public String getAccountIdEncryptedFromToken(String token) {
        return getVerifiedClaims(token)
                .get("accE", String.class);
    }

    /**
     * The {@code type} claim, or {@code null} on a token that carries none.
     *
     * <p>
     * A user access token sets no type; a refresh token sets {@link #TYPE_REFRESH};
     * a channel webhook token sets {@code CHANNEL_WEBHOOK}. Callers that accept a
     * bearer token for an API call must know which of those they were handed —
     * the three are otherwise indistinguishable, because all of them carry the
     * same tenant binding.
     */
    public String getTokenType(String token) {
        return getVerifiedClaims(token)
                .get(CLAIM_TYPE, String.class);
    }

    /**
     * The verified {@code iat} claim, or {@code null} on a token that carries
     * none. Second precision (JWT NumericDate) — callers comparing it with an
     * event time must compare whole seconds.
     */
    public Instant getIssuedAt(String token) {
        Date issuedAt = getVerifiedClaims(token)
                .getIssuedAt();
        return issuedAt != null ? issuedAt.toInstant() : null;
    }

    /** The verified {@link #CLAIM_SESSION_ID} claim, or {@code null} on a token that carries none. */
    public String getSessionId(String token) {
        return getVerifiedClaims(token)
                .get(CLAIM_SESSION_ID, String.class);
    }

    public boolean validateToken(String token) {
        try {
            getVerifiedClaims(token);
            return true;
        } catch (Exception e) {
            // Expired or foreign tokens are routine; a stack trace per request
            // flooded stderr. The class name is enough to tell them apart.
            log.debug("JWT rejected: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public long getAccessExpiration() {
        return accessExpiration;
    }
}
