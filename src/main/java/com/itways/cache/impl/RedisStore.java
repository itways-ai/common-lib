package com.itways.cache.impl;

import com.itways.cache.CacheStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisStore<K, V> implements CacheStore<K, V> {

    private final RedisTemplate<Object, Object> redisTemplate;
    private final String cacheName;
    private final long ttlMinutes;

    public RedisStore(RedisTemplate<Object, Object> redisTemplate, String cacheName, long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.cacheName = cacheName;
        this.ttlMinutes = ttlMinutes;
    }

    private String getFullName(K key) {
        return cacheName + '.' + key.toString();
    }

    @Override
    public void put(K key, V value) {
        if (redisTemplate == null) {
            log.warn("[Redis] PUT skipped — redisTemplate is null. Cache: {}, Key: {}", cacheName, key);
            return;
        }
        try {
            redisTemplate.opsForValue().set(getFullName(key), value, ttlMinutes, TimeUnit.MINUTES);
            log.debug("[Redis] PUT key='{}' TTL={}min", getFullName(key), ttlMinutes);
        } catch (Exception e) {
            log.error("[Redis] PUT failed for key='{}': {}", getFullName(key), e.getMessage());
        }
    }

    @Override
    public Optional<V> get(K key) {
        if (redisTemplate == null) {
            log.warn("[Redis] GET skipped — redisTemplate is null. Cache: {}, Key: {}", cacheName, key);
            return Optional.empty();
        }
        try {
            Object value = redisTemplate.opsForValue().get(getFullName(key));
            if (value != null) {
                log.debug("[Redis] GET HIT key='{}'", getFullName(key));
            } else {
                log.debug("[Redis] GET MISS key='{}'", getFullName(key));
            }
            return Optional.ofNullable((V) value);
        } catch (Exception e) {
            log.error("[Redis] GET failed for key='{}': {}", getFullName(key), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean containsKey(K key) {
        if (redisTemplate == null) return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(getFullName(key)));
        } catch (Exception e) {
            log.error("[Redis] containsKey failed for key='{}': {}", getFullName(key), e.getMessage());
            return false;
        }
    }

    @Override
    public void remove(K key) {
        if (redisTemplate == null) return;
        try {
            redisTemplate.delete(getFullName(key));
            log.debug("[Redis] REMOVE key='{}'", getFullName(key));
        } catch (Exception e) {
            log.error("[Redis] REMOVE failed for key='{}': {}", getFullName(key), e.getMessage());
        }
    }

    @Override
    public boolean remove(K key, V value) {
        if (redisTemplate == null) return false;
        try {
            V current = (V) redisTemplate.opsForValue().get(getFullName(key));
            if (value.equals(current)) {
                boolean deleted = Boolean.TRUE.equals(redisTemplate.delete(getFullName(key)));
                log.debug("[Redis] CONDITIONAL REMOVE key='{}' result={}", getFullName(key), deleted);
                return deleted;
            }
        } catch (Exception e) {
            log.error("[Redis] CONDITIONAL REMOVE failed for key='{}': {}", getFullName(key), e.getMessage());
        }
        return false;
    }

    @Override
    public void clear() {
        if (redisTemplate == null) return;
        try {
            var keys = redisTemplate.keys(cacheName + ".*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("[Redis] CLEAR cache='{}' — deleted {} keys", cacheName, keys.size());
            }
        } catch (Exception e) {
            log.error("[Redis] CLEAR failed for cache='{}': {}", cacheName, e.getMessage());
        }
    }
}
