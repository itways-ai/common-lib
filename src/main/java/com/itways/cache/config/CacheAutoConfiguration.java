package com.itways.cache.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * Ordered before Spring Boot's own cache auto-configuration, so that when a
 * service enables caching the {@code CacheManager} is common-lib's bounded one
 * (see {@link CacheProviderConfig}) and not whatever Boot would pick from the
 * classpath (Redis is on every service's).
 */
@AutoConfiguration(before = org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration.class)
@EnableConfigurationProperties(CacheProperties.class)
@ComponentScan(basePackages = "com.itways.cache")
public class CacheAutoConfiguration {
}
