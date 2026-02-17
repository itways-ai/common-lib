package com.itways.cache.impl;

import com.itways.cache.CacheConfig;
import com.itways.cache.CacheStore;
import com.itways.cache.CacheStoreFactory;
import com.itways.cache.config.CacheProperties;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;

import java.time.Duration;

public class EhcacheStoreFactory implements CacheStoreFactory {

    private final CacheManager cacheManager;
    private final CacheProperties properties;

    public EhcacheStoreFactory(CacheProperties properties) {
        // Initialize basic CacheManager. Caches will be added dynamically.
        this.cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
        this.properties = properties;
        init();
    }

    private void init() {
        if (properties.getCaches() != null) {
            properties.getCaches().forEach((name, config) -> {
                if (config.getName() == null) {
                    config.setName(name);
                }
                createCache(name, String.class, String.class, config);
            });
        }
    }

    @Override
    public CacheStore<String, String> getCache(String cacheName) {
        Cache<String, String> cache = cacheManager.getCache(cacheName, String.class, String.class);
        if (cache != null) {
            return new EhcacheStore<>(cache);
        }
        throw new IllegalStateException(
                "Cache '" + cacheName + "' not found. Ensure it is configured in application.properties.");
    }

    @Override
    public <K, V> CacheStore<K, V> createCache(String cacheName, Class<K> keyType, Class<V> valueType,
            CacheConfig config) {
        // Check if cache already exists to avoid exceptions
        Cache<K, V> existingCache = cacheManager.getCache(cacheName, keyType, valueType);
        if (existingCache != null) {
            return new EhcacheStore<>(existingCache);
        }

        synchronized (cacheManager) {
            // Double-check locking
            existingCache = cacheManager.getCache(cacheName, keyType, valueType);
            if (existingCache != null) {
                return new EhcacheStore<>(existingCache);
            }

            Cache<K, V> cache = cacheManager.createCache(cacheName,
                    CacheConfigurationBuilder.newCacheConfigurationBuilder(keyType, valueType,
                            ResourcePoolsBuilder.newResourcePoolsBuilder().heap(config.getHeapSize(),
                                    EntryUnit.ENTRIES))
                            .withExpiry(new ExpiryPolicyConfig<>(
                                    Duration.ofMinutes(config.getTtlMinutes()),
                                    config.isResetTtlOnUpdate())));
            return new EhcacheStore<>(cache);
        }
    }

    @Override
    public CacheStore<String, String> createCache(String cacheName) {
        CacheConfig config = properties.getCaches().get(cacheName);
        if (config == null) {
            config = CacheConfig.builder()
                    .name(cacheName)
                    .ttlMinutes(properties.getEhcache().getTtlMinutes())
                    .heapSize(properties.getEhcache().getHeapSize())
                    .resetTtlOnUpdate(properties.getEhcache().isResetTtlOnUpdate())
                    .build();
        }

        // Ensure name is set (if retrieved from map it might be null if not set in
        // setters)
        if (config.getName() == null) {
            config.setName(cacheName);
        }
        // Delegates to generic createCache which handles existence checks (idempotent)
        return createCache(cacheName, String.class, String.class, config);
    }
}
