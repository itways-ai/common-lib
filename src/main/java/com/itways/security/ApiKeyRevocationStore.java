package com.itways.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis-backed deny-list for revoked API keys, shared by every service that
 * accepts {@code X-API-KEY}.
 *
 * <p>Write side: account-service calls {@link #revoke} when a key's status is
 * flipped to REVOKED, storing {@code nibras:apikeys:revoked:<keyHash>} with a
 * TTL matching the key's remaining life (no TTL when the key never expires).
 *
 * <p>Read side: {@link ApiKeyAuthenticationFilter} calls {@link #isRevoked}
 * per request. A small in-memory verdict cache (60s) keeps the hot path to one
 * Redis round-trip per key per minute — which also bounds how long a freshly
 * revoked key keeps working on a warm instance.
 *
 * <p>Availability over lockout (pre-production posture, deliberate): when
 * Redis is unreachable the check logs a WARN and ALLOWS the key rather than
 * failing every API-key request in the platform. Flip this to fail-closed
 * before any real production exposure.
 */
@Component
@Slf4j
public class ApiKeyRevocationStore {

    static final String KEY_PREFIX = "nibras:apikeys:revoked:";

    /** How long a verdict (revoked or not) is trusted without re-asking Redis. */
    private static final long VERDICT_TTL_MILLIS = 60_000L;

    /** Safety valve so the verdict cache cannot grow unbounded under key-spraying. */
    private static final int MAX_CACHED_VERDICTS = 10_000;

    private final RedisTemplate<Object, Object> redisTemplate;
    private final Map<String, Verdict> verdictCache = new ConcurrentHashMap<>();

    public ApiKeyRevocationStore(ObjectProvider<RedisTemplate<Object, Object>> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    /**
     * Marks a key hash revoked. {@code remainingLife} bounds the Redis entry's
     * TTL to the key's own expiry; {@code null} (or non-positive) means the key
     * never expires, so the deny-list entry is kept without TTL.
     */
    public void revoke(String keyHash, Duration remainingLife) {
        if (keyHash == null || keyHash.isBlank()) {
            return;
        }
        if (redisTemplate == null) {
            log.warn("[API-KEY] Redis not configured — revocation of key hash could not be propagated to the deny-list");
            return;
        }
        try {
            String redisKey = KEY_PREFIX + keyHash;
            if (remainingLife != null && !remainingLife.isNegative() && !remainingLife.isZero()) {
                redisTemplate.opsForValue().set(redisKey, "1", remainingLife);
            } else {
                redisTemplate.opsForValue().set(redisKey, "1");
            }
            verdictCache.put(keyHash, new Verdict(true, Long.MAX_VALUE));
            log.info("[API-KEY] Key hash added to revocation deny-list");
        } catch (Exception e) {
            // The DB status is already REVOKED; losing the Redis write only
            // delays enforcement. Surface it loudly but do not fail the revoke.
            log.error("[API-KEY] Failed to write revocation to Redis: {}", e.getMessage());
        }
    }

    /**
     * Whether the key hash is on the deny-list. Verdicts are cached in-memory
     * for {@value #VERDICT_TTL_MILLIS} ms; when Redis is unreachable the
     * answer is {@code false} (allow) — availability over lockout, see class
     * javadoc.
     */
    public boolean isRevoked(String keyHash) {
        if (keyHash == null || keyHash.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Verdict cached = verdictCache.get(keyHash);
        if (cached != null && cached.validUntilMillis() > now) {
            return cached.revoked();
        }
        if (redisTemplate == null) {
            log.warn("[API-KEY] Redis not configured — skipping revocation check (allowing key)");
            return false;
        }
        try {
            boolean revoked = Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + keyHash));
            if (verdictCache.size() >= MAX_CACHED_VERDICTS) {
                verdictCache.clear();
            }
            // A revocation never comes back, so cache that verdict forever.
            verdictCache.put(keyHash, new Verdict(revoked, revoked ? Long.MAX_VALUE : now + VERDICT_TTL_MILLIS));
            return revoked;
        } catch (Exception e) {
            log.warn("[API-KEY] Redis unreachable during revocation check — allowing key ({})", e.getMessage());
            return false;
        }
    }

    private record Verdict(boolean revoked, long validUntilMillis) {
    }
}
