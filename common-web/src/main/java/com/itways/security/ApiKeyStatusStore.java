package com.itways.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed ALLOW-list of usable API keys, shared by every service that
 * accepts {@code X-API-KEY}. Replaced the Redis deny-list of revoked keys
 * (AS-08; the deny-list's store was deleted in 2.1.0, its
 * {@code nibras:apikeys:revoked:*} entries are no longer read).
 *
 * <p>Why an allow-list: with a deny-list, losing a Redis entry (eviction,
 * restart without persistence, FLUSHALL) silently resurrected a revoked key.
 * Here a key is accepted only while {@code nibras:apikeys:active:<keyHash>}
 * exists, so losing Redis data can make keys temporarily unusable but can
 * never bring a revoked key back.
 *
 * <p>Write side: account-service is the only writer. It calls
 * {@link #activate} when a key is created, {@link #deactivate} when it is
 * revoked, and periodically re-syncs the whole list from its {@code api_keys}
 * table (activating ACTIVE, unexpired keys and deactivating the rest). That
 * resync is also what repairs a write that failed here — so after a Redis
 * data loss, keys stay unusable only until the next resync.
 *
 * <p>Read side: {@code ApiKeyAuthenticationFilter} calls {@link #isActive} on
 * every API-key request. There is deliberately NO in-memory verdict cache: a
 * cached "active" answer is exactly what kept a revoked key working on other
 * services for up to a minute. When Redis is not configured or unreachable
 * the answer is {@code false} — fail closed.
 *
 * <p>Usage tracking (AS-23): {@link #recordUse} writes
 * {@code nibras:apikeys:lastused:<keyHash>} (epoch millis) at most once per
 * minute per key per instance; account-service reads it back with
 * {@link #lastUsed} to fill {@code lastUsedAt}.
 *
 * <p>Uses {@link StringRedisTemplate}, not {@code RedisTemplate<Object,Object>}:
 * services configure that template with different key serializers (JSON vs
 * JDK), so a key written by account-service would not be found by another
 * service. Plain strings are identical everywhere.
 */
@Component("apiKeyStatusStore")
@Slf4j
public class ApiKeyStatusStore {

    static final String ACTIVE_PREFIX = "nibras:apikeys:active:";
    static final String LAST_USED_PREFIX = "nibras:apikeys:lastused:";

    /** A key's last-used time is written at most this often per instance. */
    private static final long RECORD_INTERVAL_MILLIS = 60_000L;

    /** Last-used entries outlive any sensible "unused for N days" report. */
    private static final Duration LAST_USED_TTL = Duration.ofDays(90);

    /** Safety valve so the last-write map cannot grow unbounded under key-spraying. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    /** A Redis outage hits every API-key request; say so once a minute, not per request. */
    private static final long WARN_INTERVAL_MILLIS = 60_000L;

    private final StringRedisTemplate redis;
    private final Map<String, Long> lastRecordedAt = new ConcurrentHashMap<>();
    private final AtomicLong lastWarnAt = new AtomicLong();

    public ApiKeyStatusStore(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    /**
     * Puts a key hash on the allow-list. {@code ttl} bounds the entry to the
     * key's own expiry; {@code null} or non-positive stores it without TTL
     * (a key that never expires). The filter's embedded-expiry check still
     * refuses an expired key, whatever this entry says.
     *
     * <p>Never throws: a failed write only leaves the key unusable until the
     * next resync from the {@code api_keys} table.
     */
    public void activate(String keyHash, Duration ttl) {
        if (isBlank(keyHash)) {
            return;
        }
        if (redis == null) {
            log.warn("[API-KEY] Redis not configured — key hash could not be added to the allow-list");
            return;
        }
        try {
            String redisKey = ACTIVE_PREFIX + keyHash;
            if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
                redis.opsForValue().set(redisKey, "1", ttl);
            } else {
                redis.opsForValue().set(redisKey, "1");
            }
            log.debug("[API-KEY] Key hash added to the allow-list");
        } catch (Exception e) {
            log.error("[API-KEY] Failed to add key hash to the allow-list ({}): {}",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Removes a key hash from the allow-list; takes effect on the very next
     * request on every service, since nothing caches the verdict.
     *
     * <p>Never throws. If the delete fails because Redis is down, reads fail
     * too and the key is refused anyway (fail closed); the next resync removes
     * the entry for good.
     */
    public void deactivate(String keyHash) {
        if (isBlank(keyHash)) {
            return;
        }
        if (redis == null) {
            log.warn("[API-KEY] Redis not configured — key hash could not be removed from the allow-list");
            return;
        }
        try {
            redis.delete(ACTIVE_PREFIX + keyHash);
            log.info("[API-KEY] Key hash removed from the allow-list");
        } catch (Exception e) {
            log.error("[API-KEY] Failed to remove key hash from the allow-list ({}): {}",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Whether the key hash is on the allow-list — one Redis {@code EXISTS} per
     * call, never cached. Redis not configured or unreachable → {@code false}
     * (fail closed), with a WARN at most once per minute.
     */
    public boolean isActive(String keyHash) {
        if (isBlank(keyHash)) {
            return false;
        }
        if (redis == null) {
            warnThrottled("[API-KEY] Redis not configured — API keys cannot be verified and are refused");
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(ACTIVE_PREFIX + keyHash));
        } catch (Exception e) {
            warnThrottled("[API-KEY] Redis unreachable during allow-list check — refusing API keys ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            return false;
        }
    }

    /**
     * Records that the key was just used. Throttled to one Redis write per key
     * per {@value #RECORD_INTERVAL_MILLIS} ms on this instance, so the hot path
     * stays read-only. Never throws — usage tracking must not fail a request.
     */
    public void recordUse(String keyHash) {
        if (isBlank(keyHash) || redis == null) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            Long previous = lastRecordedAt.get(keyHash);
            if (previous != null && now - previous < RECORD_INTERVAL_MILLIS) {
                return;
            }
            if (previous == null && lastRecordedAt.size() >= MAX_TRACKED_KEYS) {
                lastRecordedAt.clear();
            }
            // Claim the slot atomically so concurrent requests on one key write once.
            boolean claimed = previous == null
                    ? lastRecordedAt.putIfAbsent(keyHash, now) == null
                    : lastRecordedAt.replace(keyHash, previous, now);
            if (!claimed) {
                return;
            }
            redis.opsForValue().set(LAST_USED_PREFIX + keyHash, Long.toString(now), LAST_USED_TTL);
        } catch (Exception e) {
            log.debug("[API-KEY] Failed to record key usage ({})", e.getClass().getSimpleName());
        }
    }

    /**
     * Last-used instants for the given key hashes, in one Redis {@code MGET}.
     * A hash never used (or whose entry expired) is absent from the result;
     * a Redis failure yields an empty map. Never {@code null}.
     */
    public Map<String, Instant> lastUsed(Collection<String> keyHashes) {
        Map<String, Instant> result = new HashMap<>();
        if (keyHashes == null || keyHashes.isEmpty()) {
            return result;
        }
        List<String> hashes = keyHashes.stream().filter(Objects::nonNull).filter(h -> !h.isBlank()).distinct()
                .toList();
        if (hashes.isEmpty()) {
            return result;
        }
        if (redis == null) {
            warnThrottled("[API-KEY] Redis not configured — last-used times are unavailable");
            return result;
        }
        try {
            List<String> values = redis.opsForValue()
                    .multiGet(hashes.stream().map(h -> LAST_USED_PREFIX + h).toList());
            if (values == null) {
                return result;
            }
            for (int i = 0; i < hashes.size() && i < values.size(); i++) {
                Instant at = parseEpochMillis(values.get(i));
                if (at != null) {
                    result.put(hashes.get(i), at);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[API-KEY] Failed to read last-used times ({}): {}", e.getClass().getSimpleName(),
                    e.getMessage());
            return new HashMap<>();
        }
    }

    private static Instant parseEpochMillis(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void warnThrottled(String message) {
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        if (now - last >= WARN_INTERVAL_MILLIS && lastWarnAt.compareAndSet(last, now)) {
            log.warn(message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
