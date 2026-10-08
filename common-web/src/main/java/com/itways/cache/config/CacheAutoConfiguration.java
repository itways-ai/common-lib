package com.itways.cache.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Ordered before Spring Boot's own cache auto-configuration, so that when a
 * service enables caching the {@code CacheManager} is common-lib's bounded one
 * (see {@link CacheProviderConfig}) and not whatever Boot would pick from the
 * classpath (Redis is on every service's). The cache beans are
 * {@link CacheProviderConfig}'s; it used to component-scan {@code com.itways.cache}.
 */
@AutoConfiguration(before = org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration.class)
@EnableConfigurationProperties(CacheProperties.class)
@Import(CacheProviderConfig.class)
public class CacheAutoConfiguration {
}
