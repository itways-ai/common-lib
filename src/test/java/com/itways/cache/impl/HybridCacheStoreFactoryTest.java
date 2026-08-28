package com.itways.cache.impl;

import com.itways.cache.CacheConfig;
import com.itways.cache.CacheStore;
import com.itways.cache.config.CacheProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HybridCacheStoreFactory holds the one routing decision of the cache module:
 * Redis when it is both configured and answering, the local Ehcache otherwise.
 * The predicate is pinned from both sides — provider string (case-insensitive)
 * AND live health check — plus the short-circuit that keeps non-Redis
 * deployments from paying a network probe on every cache creation.
 *
 * EhcacheStoreFactory and RedisStoreFactory are Mockito mocks throughout:
 * mocks bypass constructors, which matters because the real EhcacheStoreFactory
 * constructor builds (and leaks) a live CacheManager.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HybridCacheStoreFactory")
class HybridCacheStoreFactoryTest {

    @Mock
    private EhcacheStoreFactory ehcacheFactory;

    @Mock
    private RedisStoreFactory redisStoreFactory;

    @Mock
    private RedisHealthChecker redisHealthChecker;

    @Mock
    private CacheStore<String, String> redisStore;

    @Mock
    private CacheStore<String, String> ehcacheStore;

    private final CacheProperties properties = new CacheProperties();
    private final CacheConfig config = CacheConfig.defaultConfig();

    private HybridCacheStoreFactory factory() {
        return new HybridCacheStoreFactory(ehcacheFactory, redisStoreFactory, redisHealthChecker, properties);
    }

    @Nested
    @DisplayName("createCache routing")
    class CreateCache {

        @Test
        @DisplayName("provider 'redis' with a healthy Redis routes to the Redis factory")
        void redisHealthyRoutesToRedis() {
            properties.setProvider("redis");
            when(redisHealthChecker.isAvailable()).thenReturn(true);
            when(redisStoreFactory.createCache("session", String.class, String.class, config))
                    .thenReturn(redisStore);

            assertThat(factory().createCache("session", String.class, String.class, config))
                    .isSameAs(redisStore);
            verifyNoInteractions(ehcacheFactory);
        }

        @Test
        @DisplayName("the provider string is matched case-insensitively")
        void providerCaseInsensitive() {
            // Deployment configs write 'REDIS', 'Redis', 'redis' — routing must
            // not silently fall back to the local cache over casing.
            properties.setProvider("REDIS");
            when(redisHealthChecker.isAvailable()).thenReturn(true);
            when(redisStoreFactory.createCache("session", String.class, String.class, config))
                    .thenReturn(redisStore);

            assertThat(factory().createCache("session", String.class, String.class, config))
                    .isSameAs(redisStore);
        }

        @Test
        @DisplayName("provider 'redis' with Redis down falls back to Ehcache")
        void redisDownFallsBackToEhcache() {
            // NOTE: possible defect — the fallback is decided per call, not
            // sticky: a store created during a Redis outage stays Ehcache-backed
            // forever while later creations return Redis-backed stores, so the
            // same cache name can serve two disjoint data sets after recovery.
            // Only the per-call selection is pinned here.
            properties.setProvider("redis");
            when(redisHealthChecker.isAvailable()).thenReturn(false);
            when(ehcacheFactory.createCache("session", String.class, String.class, config))
                    .thenReturn(ehcacheStore);

            assertThat(factory().createCache("session", String.class, String.class, config))
                    .isSameAs(ehcacheStore);
            verifyNoInteractions(redisStoreFactory);
        }

        @Test
        @DisplayName("a non-redis provider routes to Ehcache without ever probing Redis health")
        void nonRedisProviderSkipsHealthProbe() {
            // The && short-circuit is what keeps the default (ehcache) profile
            // free of a network round-trip per cache creation.
            properties.setProvider("ehcache");
            when(ehcacheFactory.createCache("session", String.class, String.class, config))
                    .thenReturn(ehcacheStore);

            assertThat(factory().createCache("session", String.class, String.class, config))
                    .isSameAs(ehcacheStore);
            verifyNoInteractions(redisHealthChecker, redisStoreFactory);
        }
    }

    @Nested
    @DisplayName("getCache routing")
    class GetCache {

        @Test
        @DisplayName("resolves via Redis when the provider is redis and Redis is healthy")
        void redisHealthy() {
            properties.setProvider("redis");
            when(redisHealthChecker.isAvailable()).thenReturn(true);
            when(redisStoreFactory.getCache("session")).thenReturn(redisStore);

            assertThat(factory().getCache("session")).isSameAs(redisStore);
            verifyNoInteractions(ehcacheFactory);
        }

        @Test
        @DisplayName("resolves via Ehcache when Redis is unavailable")
        void redisDown() {
            properties.setProvider("redis");
            when(redisHealthChecker.isAvailable()).thenReturn(false);
            when(ehcacheFactory.getCache("session")).thenReturn(ehcacheStore);

            assertThat(factory().getCache("session")).isSameAs(ehcacheStore);
            verifyNoInteractions(redisStoreFactory);
        }
    }
}
