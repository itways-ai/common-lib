package com.itways.cache;

import java.util.Optional;

public interface CacheStore<K, V> {

    /**
     * Store a value in the cache with default TTL
     */
    void put(K key, V value);

    /**
     * Retrieve a value from the cache
     */
    Optional<V> get(K key);

    /**
     * Check if key exists
     */
    boolean containsKey(K key);

    /**
     * Remove an entry
     */
    void remove(K key);

    /**
     * Atomically remove an entry only if it matches the expected value
     * 
     * @return true if removed, false otherwise
     */
    boolean remove(K key, V value);

    /**
     * Clear all entries in this cache instance
     */
    void clear();
}
