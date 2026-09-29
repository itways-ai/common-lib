package com.itways.cache.config;

import com.itways.cache.CacheSettings;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "itways.cache")
public class CacheProperties {

    /**
     * Cache provider type. Options: ehcache, inmemory
     */
    private String provider = "ehcache";

    /**
     * Default Ehcache configuration properties
     */
    private EhcacheProperties ehcache = new EhcacheProperties();

    /**
     * Default Redis configuration properties
     */
    private RedisProperties redis = new RedisProperties();

    @Data
    public static class EhcacheProperties {
        /**
         * Default max heap size (number of entries)
         */
        private int heapSize = 1000;

        /**
         * Default TTL in minutes
         */
        private int ttlMinutes = 10;

        /**
         * Whether to reset TTL on update. Default is false.
         */
        private boolean resetTtlOnUpdate = false;
    }

    @Data
    public static class RedisProperties {
        /**
         * Default TTL in minutes for Redis cache entries
         */
        private int ttlMinutes = 10;
    }

    /**
     * The Spring {@code CacheManager} common-lib registers for {@code @Cacheable}
     * (see {@code CacheProviderConfig#cacheManager}).
     */
    private ManagerProperties manager = new ManagerProperties();

    @Data
    public static class ManagerProperties {
        /**
         * Most entries each cache holds; the least recently used go first
         * ({@code itways.cache.manager.maximum-size}).
         */
        private long maximumSize = 10_000;

        /**
         * How long an entry lives after it was written
         * ({@code itways.cache.manager.ttl}, e.g. {@code 10m}, {@code 1h}).
         */
        private Duration ttl = Duration.ofMinutes(10);
    }

    /**
     * Map of cache configurations
     */
    private Map<String, CacheSettings> caches = new HashMap<>();
}
