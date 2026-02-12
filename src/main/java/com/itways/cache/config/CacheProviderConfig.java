package com.itways.cache.config;

import com.itways.cache.CacheStoreFactory;
import com.itways.cache.impl.EhcacheCacheStoreFactory;
import com.itways.cache.impl.InMemoryCacheStoreFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheProviderConfig {
    @Bean
    @ConditionalOnProperty(name = "itways.cache.provider", havingValue = "ehcache", matchIfMissing = true)
    @ConditionalOnMissingBean
    public CacheStoreFactory ehcacheCacheStoreFactory(CacheProperties properties) {
        return new EhcacheCacheStoreFactory(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "itways.cache.provider", havingValue = "inmemory")
    @ConditionalOnMissingBean
    public CacheStoreFactory inMemoryCacheStoreFactory() {
        return new InMemoryCacheStoreFactory();
    }
}
