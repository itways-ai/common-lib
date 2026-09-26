package com.itways.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import com.itways.contracts.channels.ChannelWebhookTokenClaims;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.rsa.private-key:}")
    private String privateKeyStr;

    /** Claim naming what a token is for; absent on a user access token. */
    public static final String CLAIM_TYPE = "type";

    /** A refresh token: good only for minting a new access token, never for an API call. */
    public static final String TYPE_REFRESH = "REFRESH";

    /** What a token with no {@link #CLAIM_TYPE} is: an ordinary user access token. */
    public static final String TYPE_ACCESS = "ACCESS";

    @Value("${jwt.rsa.public-key:}")
    private String publicKeyStr;

    /**
     * The public half of the key channels-service signs webhook URLs with
     * ({@code CHANNEL_WEBHOOK_PUBLIC_KEY}). Optional: without it a webhook
     * token is verified with the platform key, as before the split.
     */
    @Value("${jwt.channel-webhook.public-key:${CHANNEL_WEBHOOK_PUBLIC_KEY:}}")
    private String webhookPublicKeyStr;

    @Value("${jwt.access-expiration:3600000}") // 1 hour
    private long accessExpiration;

    @Value("${jwt.refresh-expiration:604800000}") // 7 days
    private long refreshExpiration;

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private PublicKey webhookPublicKey;

    @PostConstruct
    public void init() throws Exception {
        boolean hasPrivate = privateKeyStr != null && !privateKeyStr.isEmpty();
        boolean hasPublic = publicKeyStr != null && !publicKeyStr.isEmpty();

        if (hasPublic) {
            // Verify-only is a first-class mode: only auth-service signs
            // tokens, so every other service is configured with just the
            // public key and never sees the private key.
            this.publicKey = loadPublicKey(publicKeyStr);
            this.privateKey = hasPrivate ? loadPrivateKey(privateKeyStr) : null;
        } else if (hasPrivate) {
            throw new IllegalStateException(
                    "jwt.rsa.private-key is set but jwt.rsa.public-key is not — configure the public key too");
        } else {
            // Generate for development if not provided
            KeyPair keyPair = Keys.keyPairFor(io.jsonwebtoken.SignatureAlgorithm.RS512);
            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();
            System.out.println("DEBUG: Generated temporary RSA keys for JWT.");
        }
        if (webhookPublicKeyStr != null && !webhookPublicKeyStr.isBlank()) {
            this.webhookPublicKey = loadPublicKey(webhookPublicKeyStr.trim());
        }
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
     * credential signed with the platform key is refused.
     *
     * @throws JwtException when the signature, expiry or key rules fail
     */
    public Claims getVerifiedClaims(String token) {
        boolean[] webhookKeyUsed = { false };
        Claims claims = Jwts.parser()
                .keyLocator(new LocatorAdapter<Key>() {
                    @Override
                    protected Key locate(ProtectedHeader header) {
                        if (ChannelWebhookTokenClaims.KEY_ID.equals(header.getKeyId())) {
                            if (webhookPublicKey == null) {
                                throw new JwtException("Token signed with the webhook key, which is not configured here");
                            }
                            webhookKeyUsed[0] = true;
                            return webhookPublicKey;
                        }
                        return publicKey;
                    }
                })
                .build()
                .parseSignedClaims(token)
                .getPayload();
        boolean webhookType = ChannelWebhookTokenClaims.TYPE_CHANNEL_WEBHOOK.equals(claims.get(CLAIM_TYPE, String.class));
        if (webhookKeyUsed[0] && !webhookType) {
            throw new JwtException("Only a channel webhook token may be signed with the webhook key");
        }
        if (webhookType && webhookPublicKey != null && !webhookKeyUsed[0]) {
            throw new JwtException("Channel webhook tokens must be signed with the webhook key");
        }
        return claims;
    }

    private PrivateKey requireSigningKey() {
        if (privateKey == null) {
            throw new IllegalStateException(
                    "No jwt.rsa.private-key configured — this service verifies tokens but cannot mint them");
        }
        return privateKey;
    }

    private PrivateKey loadPrivateKey(String key) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private PublicKey loadPublicKey(String key) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
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
