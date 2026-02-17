package com.itways.cache;

public interface CacheStoreFactory {

    /**
     * Create or retrieve a cache instance
     * 
     * @param cacheName Unique name for the cache
     * @param keyType   Class type of the key
     * @param valueType Class type of the value
     * @param config    Configuration for this specific cache
     * @return CacheStore instance
     */
    <K, V> CacheStore<K, V> createCache(String cacheName, Class<K> keyType, Class<V> valueType, CacheConfig config);

    /**
     * Retrieve an existing cache instance
     *
     * @param cacheName Unique name of the cache
     * @return CacheStore instance or null if not found
     */
    CacheStore<String, String> getCache(String cacheName);

    /**
     * Create or retrieve a cache instance using configuration from properties
     *
     * @param cacheName Unique name for the cache
     * @return CacheStore instance with String keys and values
     */
    default CacheStore<String, String> createCache(String cacheName) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
