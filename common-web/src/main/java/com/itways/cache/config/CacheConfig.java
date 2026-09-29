package com.itways.cache.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** What {@code @EnableCache} brings: {@link CacheProviderConfig} (it used to component-scan {@code com.itways.cache}). */
@Configuration("cacheConfig")
@EnableConfigurationProperties(CacheProperties.class)
@Slf4j
@Import(CacheProviderConfig.class)
public class CacheConfig {
    @PostConstruct
    public void print() {
        log.info("✅ Common-lib Cache configuration initialized");
    }

}
