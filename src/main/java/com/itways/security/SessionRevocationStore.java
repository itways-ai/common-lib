package com.itways.security;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Ends the ACCESS tokens a user held before changing their password (AS-03,
 * C08), across every service.
 *
 * <p>Access tokens are stateless and live up to an hour, so without this a
 * stolen token kept working after the victim changed their password. The
 * auth-service calls {@link #markPasswordChanged} when a password changes,
 * storing {@code nibras:auth:pwchanged:<accountId>} = the change time in epoch
 * seconds; {@code JwtAuthenticationFilter} then refuses any access token of
 * that account whose {@code iat} is strictly earlier ({@link #isRevoked}).
 * The comparison is strict because {@code iat} has second precision: the
 * fresh token pair returned by the change itself is minted in the same second
 * and must survive. Refresh tokens are checked by auth-service against its own
 * {@code password_changed_at} column, not here.
 *
 * <p>The entry only has to outlive the tokens it revokes, so it expires after
 * {@code security.session-revocation.ttl} (default 2 days — far beyond the
 * access-token lifetime; raise it if access tokens ever live longer).
 *
 * <p>Fail OPEN, deliberately: this is defence in depth on top of short-lived
 * tokens. If Redis is missing or unreachable the check answers "not revoked"
 * (and WARNs at most once a minute) — failing closed would log out every user
 * on the platform whenever Redis blips. No in-memory caching, so a change
 * takes effect on the very next request on every service.
 *
 * <p>Uses {@link StringRedisTemplate} so the key and value read the same in
 * every service, whatever each one configured for its
 * {@code RedisTemplate<Object,Object>}.
 */
@Component
@Slf4j
public class SessionRevocationStore {

    static final String KEY_PREFIX = "nibras:auth:pwchanged:";

    static final Duration DEFAULT_TTL = Duration.ofDays(2);

    /** A Redis outage hits every bearer request; say so once a minute, not per request. */
    private static final long WARN_INTERVAL_MILLIS = 60_000L;

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final AtomicLong lastWarnAt = new AtomicLong();

    public SessionRevocationStore(ObjectProvider<StringRedisTemplate> redisProvider,
            @Value("${security.session-revocation.ttl:2d}") String ttl) {
        this.redis = redisProvider.getIfAvailable();
        this.ttl = parseTtl(ttl);
    }

    /**
     * Records a password change, so access tokens issued before {@code when}
     * stop working everywhere. {@code null} means now.
     *
     * <p>Never throws: the password is already changed; a failed write only
     * leaves older access tokens valid until they expire (the behaviour before
     * this store existed).
     */
    public void markPasswordChanged(String accountId, Instant when) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        Instant changedAt = when != null ? when : Instant.now();
        if (redis == null) {
            log.warn("[SESSION] Redis not configured — older access tokens stay valid until they expire");
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + accountId, Long.toString(changedAt.getEpochSecond()), ttl);
            log.info("[SESSION] Access tokens issued before the password change are now revoked");
        } catch (Exception e) {
            log.error("[SESSION] Failed to record the password change ({}): {}", e.getClass().getSimpleName(),
                    e.getMessage());
        }
    }

    /**
     * Whether a token of {@code accountId} issued at {@code issuedAt} predates
     * the account's last password change. One Redis {@code GET}, never cached.
     * A token without {@code iat} cannot prove it is newer, so it counts as
     * revoked once a change is on record. Redis missing or unreachable →
     * {@code false} (fail open, see class javadoc).
     */
    public boolean isRevoked(String accountId, Instant issuedAt) {
        if (accountId == null || accountId.isBlank()) {
            return false;
        }
        if (redis == null) {
            warnThrottled("[SESSION] Redis not configured — password-change revocation is not enforced");
            return false;
        }
        String value;
        try {
            value = redis.opsForValue().get(KEY_PREFIX + accountId);
        } catch (Exception e) {
            warnThrottled("[SESSION] Redis unreachable — password-change revocation is not enforced ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            return false;
        }
        if (value == null || value.isBlank()) {
            return false;
        }
        long changedAtSeconds;
        try {
            changedAtSeconds = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[SESSION] Ignoring malformed password-change marker for an account");
            return false;
        }
        return issuedAt == null || issuedAt.getEpochSecond() < changedAtSeconds;
    }

    private static Duration parseTtl(String value) {
        Duration parsed = null;
        try {
            parsed = value == null || value.isBlank() ? null : DurationStyle.detectAndParse(value.trim());
        } catch (IllegalArgumentException e) {
            log.warn("[SESSION] Invalid security.session-revocation.ttl '{}' — using {}", value, DEFAULT_TTL);
        }
        if (parsed == null || parsed.isNegative() || parsed.isZero()) {
            return DEFAULT_TTL;
        }
        return parsed;
    }

    private void warnThrottled(String message) {
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        if (now - last >= WARN_INTERVAL_MILLIS && lastWarnAt.compareAndSet(last, now)) {
            log.warn(message);
        }
    }
}
