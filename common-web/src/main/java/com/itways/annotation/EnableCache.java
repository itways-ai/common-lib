package com.itways.annotation;

import com.itways.cache.config.CacheConfig;
import java.lang.annotation.*;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;

/**
 * Enables ItWays generic caching support.
 * Configures the appropriate CacheStoreFactory based on 'itways.cache.provider'
 * property.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(CacheConfig.class)
@EnableCaching
public @interface EnableCache {
}
