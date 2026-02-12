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
    }

    /**
     * Map of cache configurations
     */
    private Map<String, CacheConfig> caches = new HashMap<>();
}
