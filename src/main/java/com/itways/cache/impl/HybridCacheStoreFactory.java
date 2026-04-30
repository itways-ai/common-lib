package com.itways.cache.impl;

import com.itways.cache.CacheConfig;
import com.itways.cache.CacheStore;
import com.itways.cache.CacheStoreFactory;
import com.itways.cache.config.CacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
@RequiredArgsConstructor
@Slf4j
public class HybridCacheStoreFactory implements CacheStoreFactory {

    private final EhcacheStoreFactory ehcacheFactory;
    private final RedisStoreFactory redisStoreFactory;
    private final RedisHealthChecker redisHealthChecker;
    private final CacheProperties properties;

    @Override
    public <K, V> CacheStore<K, V> createCache(String cacheName, Class<K> keyType, Class<V> valueType, CacheConfig config) {
        if ("redis".equalsIgnoreCase(properties.getProvider()) && redisHealthChecker.isAvailable()) {
            log.info("✅ [Cache] Using Redis for cache '{}' (TTL={}min)", cacheName, config.getTtlMinutes());
            return redisStoreFactory.createCache(cacheName, keyType, valueType, config);
        }
        log.warn("⚠️ [Cache] Redis unavailable or not configured — falling back to Ehcache for '{}'", cacheName);
        return ehcacheFactory.createCache(cacheName, keyType, valueType, config);
    }

    @Override
    public CacheStore<String, String> getCache(String cacheName) {
        if ("redis".equalsIgnoreCase(properties.getProvider()) && redisHealthChecker.isAvailable()) {
            log.info("✅ [Cache] getCache '{}' resolved via Redis", cacheName);
            return redisStoreFactory.getCache(cacheName);
        }
        log.warn("⚠️ [Cache] getCache '{}' resolved via Ehcache (Redis unavailable)", cacheName);
        return ehcacheFactory.getCache(cacheName);
    }
}
