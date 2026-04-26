package com.itways.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.itways.cache.CacheConfig;
import java.util.HashMap;
import java.util.Map;

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
     * Map of cache configurations
     */
    private Map<String, CacheConfig> caches = new HashMap<>();
}
