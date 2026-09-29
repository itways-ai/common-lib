package com.itways.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Ends user ACCESS tokens before they expire, across every service (AS-03 C08,
 * PLT-05). Access tokens are stateless; without this a token kept working for
 * its whole lifetime after sign-out, a password change or a deactivation.
 *
 * <p>Two kinds of entry, both written only by auth-service and read by
 * {@code JwtAuthenticationFilter} in every servlet service:
 * <ul>
 * <li><b>Revoked before</b> — {@code nibras:auth:pwchanged:<accountId>} = a
 * cut-off in epoch seconds ({@link #revokeAllBefore}). Every access token of the
 * account whose {@code iat} is strictly earlier is dead: password change or
 * reset, and an operator's deactivation ({@code users.sessions_revoked_at}).
 * Strict, because {@code iat} has second precision and the fresh pair returned
 * by a password change is minted in the same second. The key keeps its original
 * name so services still running the previous common-lib keep reading it during
 * a rolling deploy. TTL {@code security.session-revocation.ttl} (default 2 days,
 * far beyond the access-token lifetime).</li>
 * <li><b>Revoked session</b> — {@code nibras:auth:revoked-session:<sid>} = "1"
 * ({@link #revokeSession}). Every access token carrying that {@code sid} claim
 * is dead: sign-out, and a session ended because its refresh token was reused.
 * TTL = the access-token lifetime plus {@link #CLOCK_MARGIN}; after that every
 * token of the session has expired anyway. Access tokens minted before they
 * carried a {@code sid} are not affected by this entry and die at their expiry.</li>
 * </ul>
 * Refresh tokens are not checked here: auth-service ends their session in its
 * own store and compares their {@code iat} with its database columns.
 *
 * <p>The read is one Redis round trip per request: {@code GET} of the account
 * entry, or {@code MGET} of both entries when the token has a {@code sid}.
 * No in-memory caching, so a revocation takes effect on the very next request.
 *
 * <p>Fail OPEN, deliberately — the opposite of {@link ApiKeyStatusStore}, which
 * is an allow-list and fails closed. This store is a deny-list layered on
 * short-lived tokens (15 minutes): if Redis is missing or unreachable the check
 * answers "not revoked" and WARNs at most once a minute. Failing closed would
 * sign every user of the platform out whenever Redis blips, while failing open
 * only gives a signed-out token back the rest of its (at most 15-minute) life.
 * The same holds for a lost entry (Redis restart without persistence).
 *
 * <p>Services without a {@link StringRedisTemplate} bean still start: the store
 * then enforces nothing (fail open, throttled WARN).
 *
 * <p>Uses {@link StringRedisTemplate} so the key and value read the same in
 * every service, whatever each one configured for its
 * {@code RedisTemplate<Object,Object>}.
 */
@Component("sessionRevocationStore")
@Slf4j
public class SessionRevocationStore {

    /** Revoked-before cut-off per account. Historic name: it began with password changes only. */
    static final String KEY_PREFIX = "nibras:auth:pwchanged:";

    /** One revoked session ({@code sid} claim). */
    static final String SESSION_PREFIX = "nibras:auth:revoked-session:";

    static final Duration DEFAULT_TTL = Duration.ofDays(2);

    /** Added to a revoked session's TTL so a token whose expiry is judged by a slightly slow clock is covered. */
    static final Duration CLOCK_MARGIN = Duration.ofMinutes(1);

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
     * Ends every access token of {@code accountId} issued before {@code when}
     * ({@code null} means now), on every service: password change or reset,
     * deactivation. A later call overwrites the cut-off, so pass the newest one.
     *
     * <p>Never throws: the change it follows has already happened; a failed
     * write only leaves older access tokens valid until they expire.
     */
    public void revokeAllBefore(String accountId, Instant when) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        Instant cutoff = when != null ? when : Instant.now();
        if (redis == null) {
            log.warn("[SESSION] Redis not configured — older access tokens stay valid until they expire");
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + accountId, Long.toString(cutoff.getEpochSecond()), ttl);
            log.info("[SESSION] Access tokens of the account issued before the cut-off are now revoked");
        } catch (Exception e) {
            log.error("[SESSION] Failed to record the revocation cut-off ({}): {}", e.getClass().getSimpleName(),
                    e.getMessage());
        }
    }

    /**
     * The name this method had when only password changes used it.
     *
     * @deprecated use {@link #revokeAllBefore}; this delegates to it.
     */
    @Deprecated
    public void markPasswordChanged(String accountId, Instant when) {
        revokeAllBefore(accountId, when);
    }

    /**
     * Ends every access token that carries {@code sid} = {@code sessionId}, on
     * every service (sign-out). {@code accessTokenLifetime} is how long such a
     * token can live; the entry is kept that long plus {@link #CLOCK_MARGIN}.
     * {@code null} or non-positive keeps it for {@code security.session-revocation.ttl}.
     *
     * <p>Never throws: the session itself is already ended; a failed write
     * only leaves its access tokens valid until they expire.
     */
    public void revokeSession(String sessionId, Duration accessTokenLifetime) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        if (redis == null) {
            log.warn("[SESSION] Redis not configured — the session's access tokens stay valid until they expire");
            return;
        }
        Duration entryTtl = accessTokenLifetime == null || accessTokenLifetime.isNegative()
                || accessTokenLifetime.isZero() ? ttl : accessTokenLifetime.plus(CLOCK_MARGIN);
        try {
            redis.opsForValue().set(SESSION_PREFIX + sessionId, "1", entryTtl);
            log.debug("[SESSION] Access tokens of the session are now revoked");
        } catch (Exception e) {
            log.error("[SESSION] Failed to record the revoked session ({}): {}", e.getClass().getSimpleName(),
                    e.getMessage());
        }
    }

    /** {@link #isRevoked(String, Instant, String)} for a token without a {@code sid}. */
    public boolean isRevoked(String accountId, Instant issuedAt) {
        return isRevoked(accountId, issuedAt, null);
    }

    /**
     * Whether an access token of {@code accountId}, issued at {@code issuedAt},
     * carrying {@code sid} = {@code sessionId} (may be {@code null}), is revoked:
     * its session was ended, or it predates the account's cut-off. One Redis
     * {@code GET} (no session id) or {@code MGET} (both keys), never cached.
     * A token without {@code iat} cannot prove it is newer, so it counts as
     * revoked once a cut-off is on record. Redis missing or unreachable →
     * {@code false} (fail open, see class javadoc).
     */
    public boolean isRevoked(String accountId, Instant issuedAt, String sessionId) {
        boolean hasAccount = accountId != null && !accountId.isBlank();
        boolean hasSession = sessionId != null && !sessionId.isBlank();
        if (!hasAccount && !hasSession) {
            return false;
        }
        if (redis == null) {
            warnThrottled("[SESSION] Redis not configured — access-token revocation is not enforced");
            return false;
        }
        String cutoff;
        String sessionRevoked;
        try {
            if (hasAccount && hasSession) {
                List<String> values = redis.opsForValue()
                        .multiGet(List.of(KEY_PREFIX + accountId, SESSION_PREFIX + sessionId));
                cutoff = values != null && !values.isEmpty() ? values.get(0) : null;
                sessionRevoked = values != null && values.size() > 1 ? values.get(1) : null;
            } else if (hasAccount) {
                cutoff = redis.opsForValue().get(KEY_PREFIX + accountId);
                sessionRevoked = null;
            } else {
                cutoff = null;
                sessionRevoked = redis.opsForValue().get(SESSION_PREFIX + sessionId);
            }
        } catch (Exception e) {
            warnThrottled("[SESSION] Redis unreachable — access-token revocation is not enforced ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            return false;
        }
        if (sessionRevoked != null) {
            return true;
        }
        return predatesCutoff(cutoff, issuedAt);
    }

    private static boolean predatesCutoff(String value, Instant issuedAt) {
        if (value == null || value.isBlank()) {
            return false;
        }
        long cutoffSeconds;
        try {
            cutoffSeconds = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[SESSION] Ignoring a malformed revocation cut-off for an account");
            return false;
        }
        return issuedAt == null || issuedAt.getEpochSecond() < cutoffSeconds;
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
