package com.itways.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Component
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

    @Value("${jwt.access-expiration:3600000}") // 1 hour
    private long accessExpiration;

    @Value("${jwt.refresh-expiration:604800000}") // 7 days
    private long refreshExpiration;

    private PrivateKey privateKey;
    private PublicKey publicKey;

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
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public String getAccountIdHashedFromToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("accH", String.class);
    }

    public String getAccountIdEncryptedFromToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
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
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get(CLAIM_TYPE, String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
        	e.printStackTrace();
            return false;
        }
    }

    public long getAccessExpiration() {
        return accessExpiration;
    }
}
