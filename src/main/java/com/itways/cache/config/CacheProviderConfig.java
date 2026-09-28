package com.itways.cache.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itways.cache.CacheStoreFactory;
import com.itways.cache.impl.EhcacheStoreFactory;
import com.itways.cache.impl.HybridCacheStoreFactory;
import com.itways.cache.impl.MemoryStoreFactory;
import com.itways.cache.impl.RedisHealthChecker;
import com.itways.cache.impl.RedisStoreFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class CacheProviderConfig {

    @Bean
    @ConditionalOnClass(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Plain JSON serializer — no embedded class names, so any service can deserialize
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(mapper, Object.class);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        return template;
    }

    @Bean
    public RedisStoreFactory redisCacheStoreFactory(ObjectProvider<RedisTemplate<Object, Object>> redisTemplateProvider,
                                                    CacheProperties cacheProperties) {
        return new RedisStoreFactory(redisTemplateProvider.getIfAvailable(), cacheProperties);
    }

    @Bean
    public EhcacheStoreFactory ehcacheStoreFactory(CacheProperties properties) {
        return new EhcacheStoreFactory(properties);
    }

    @Bean
    public MemoryStoreFactory memoryStoreFactory() {
        return new MemoryStoreFactory();
    }

    @Bean
    public RedisHealthChecker redisHealthChecker(ObjectProvider<RedisConnectionFactory> redisConnectionFactory) {
        return new RedisHealthChecker(redisConnectionFactory);
    }

    @Bean
    @Primary
    public CacheStoreFactory cacheStoreFactory(EhcacheStoreFactory ehcacheStoreFactory,
                                               RedisStoreFactory redisStoreFactory,
                                               RedisHealthChecker redisHealthChecker,
                                               CacheProperties cacheProperties) {
        return new HybridCacheStoreFactory(ehcacheStoreFactory, redisStoreFactory, redisHealthChecker, cacheProperties);
    }

    /**
     * The Spring {@link CacheManager} behind {@code @Cacheable}, unless the
     * service declares its own (PLT-22).
     *
     * <p>
     * It used to be an unbounded {@code ConcurrentMapCacheManager}: entries
     * never expired and a cache keyed by user input grew until the heap ran
     * out. Now every cache it creates is a Caffeine cache capped at
     * {@code itways.cache.manager.maximum-size} entries (default 10000) that
     * drops an entry {@code itways.cache.manager.ttl} after it was written
     * (default 10 minutes). Caches are still created on first use, under any
     * name. The {@link CacheStoreFactory} stores (Ehcache, Redis, hybrid) are
     * separate and unchanged.
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheManager cacheManager(CacheProperties cacheProperties) {
        CacheProperties.ManagerProperties settings = cacheProperties.getManager();
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(settings.getMaximumSize())
                .expireAfterWrite(settings.getTtl()));
        // Same as the previous manager: a method returning null is cached too.
        manager.setAllowNullValues(true);
        return manager;
    }
}
