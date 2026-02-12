package com.itways.cache.impl;

import com.itways.cache.CacheConfig;
import com.itways.cache.CacheStore;
import com.itways.cache.CacheStoreFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCacheStoreFactory implements CacheStoreFactory {

    // Keep track of caches by name to return same instance if requested again
    private final Map<String, CacheStore<?, ?>> caches = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public <K, V> CacheStore<K, V> createCache(String cacheName, Class<K> keyType, Class<V> valueType,
            CacheConfig config) {
        return (CacheStore<K, V>) caches.computeIfAbsent(cacheName, k -> new InMemoryCacheStore<K, V>(config));
    }

    @SuppressWarnings("unchecked")
    @Override
    public CacheStore<String, String> getCache(String cacheName) {
        // For in-memory, we create it if it doesn't exist to behave similarly,
        // or we could return null. Given the usage, returning a cache is safer.
        return (CacheStore<String, String>) caches.computeIfAbsent(cacheName,
                k -> new InMemoryCacheStore<String, String>(CacheConfig.defaultConfig()));
    }

    @Override
    public CacheStore<String, String> createCache(String cacheName) {
        // For in-memory, we just use default config if not provided, or we could inject
        // properties if we wanted to support it here too.
        // For now, using default config as InMemory is mostly for testing/dev without
        // ehcache.
        return createCache(cacheName, String.class, String.class, CacheConfig.defaultConfig());
    }
}
