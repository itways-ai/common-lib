package com.itways.cache.impl;

import com.itways.cache.CacheStore;
import org.ehcache.Cache;

import java.util.Optional;

public class EhcacheCacheStore<K, V> implements CacheStore<K, V> {

    private final Cache<K, V> cache;

    public EhcacheCacheStore(Cache<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public void put(K key, V value) {
        // Uses cache-level configuration for TTL
        cache.put(key, value);
    }

    @Override
    public Optional<V> get(K key) {
        return Optional.ofNullable(cache.get(key));
    }

    @Override
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }

    @Override
    public void remove(K key) {
        cache.remove(key);
    }

    @Override
    public boolean remove(K key, V value) {
        return cache.remove(key, value);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
