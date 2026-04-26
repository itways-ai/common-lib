package com.itways.cache.impl;

import com.itways.cache.CacheConfig;
import com.itways.cache.CacheStore;
import com.itways.cache.CacheStoreFactory;
import com.itways.cache.config.CacheProperties;
import org.springframework.data.redis.core.RedisTemplate;

public class RedisStoreFactory implements CacheStoreFactory {

    private final RedisTemplate<Object,Object> redisTemplate;
    private final CacheProperties cacheProperties;

    public RedisStoreFactory(RedisTemplate<Object
            , Object> redisTemplate
            , CacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public <K, V> CacheStore<K, V> createCache(String cacheName
            , Class<K> keyType
            , Class<V> valueType
            , CacheConfig config) {

        return (CacheStore<K, V>) new RedisStore<>(redisTemplate,cacheName,config.getTtlMinutes());
    }

    @Override
    public CacheStore<String, String> getCache(String cacheName) {
        return createCache(cacheName);
    }

    @Override
    public CacheStore<String, String> createCache(String cacheName) {
        int ttl = cacheProperties.getRedis().getTtlMinutes();
        return new RedisStore(redisTemplate,cacheName,ttl);
    }
}
