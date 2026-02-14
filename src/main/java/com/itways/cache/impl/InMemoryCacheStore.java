package com.itways.cache.impl;

import com.itways.cache.CacheConfig;
import com.itways.cache.CacheStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache implementation.
 * Note: Expiration cleanup is lazy (on access) or manual, not background
 * threaded in this simple version.
 */
public class InMemoryCacheStore<K, V> implements CacheStore<K, V> {

    // Wrapper to hold value and expiry
    private static class CacheEntry<V> {
        final V value;
        final long expiryTime;

        CacheEntry(V value, long ttlSeconds) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + (ttlSeconds * 1000);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    private final Map<K, CacheEntry<V>> store = new ConcurrentHashMap<>();
    private final CacheConfig config;

    public InMemoryCacheStore(CacheConfig config) {
        this.config = config;
    }

    @Override
    public void put(K key, V value) {
        store.put(key, new CacheEntry<>(value, config.getTtlMinutes() * 60L));
    }

    @Override
    public Optional<V> get(K key) {
        CacheEntry<V> entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }

    @Override
    public boolean containsKey(K key) {
        return get(key).isPresent(); // Utilizes get logic to check expiry
    }

    @Override
    public void remove(K key) {
        store.remove(key);
    }

    @Override
    public boolean remove(K key, V value) {
        boolean[] removed = new boolean[1];
        store.compute(key, (k, existingEntry) -> {
            if (existingEntry == null)
                return null;
            if (existingEntry.isExpired())
                return null; // Treat as not existing (or remove? If expired, return null to remove anyway)
            // If value matches, we mark removed=true and return null to remove from map
            if (existingEntry.value.equals(value)) {
                removed[0] = true;
                return null;
            }
            return existingEntry; // Keep existing
        });
        return removed[0];
    }

    @Override
    public void clear() {
        store.clear();
    }
}
