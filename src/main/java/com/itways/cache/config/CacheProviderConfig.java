package com.itways.cache.config;

import com.itways.cache.CacheStoreFactory;
import com.itways.cache.impl.EhcacheStoreFactory;
import com.itways.cache.impl.MemoryStoreFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

@Configuration
public class CacheProviderConfig {
    @Bean
    @ConditionalOnProperty(name = "itways.cache.provider", havingValue = "ehcache", matchIfMissing = true)
    @ConditionalOnMissingBean
    public CacheStoreFactory ehcacheCacheStoreFactory(CacheProperties properties) {
        return new EhcacheStoreFactory(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "itways.cache.provider", havingValue = "inmemory")
    @ConditionalOnMissingBean
    public CacheStoreFactory inMemoryCacheStoreFactory() {
        return new MemoryStoreFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}
