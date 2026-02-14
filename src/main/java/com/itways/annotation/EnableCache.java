package com.itways.annotation;

import com.itways.cache.config.CacheAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
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
@Import(CacheAutoConfiguration.class)
@EnableCaching
public @interface EnableCache {
}
