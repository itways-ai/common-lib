package com.itways.annotation;

import com.itways.cache.config.ItWaysCacheAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enables ItWays generic caching support.
 * Configures the appropriate CacheStoreFactory based on 'itways.cache.provider'
 * property.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ItWaysCacheAutoConfiguration.class)
public @interface EnableCache {
}
