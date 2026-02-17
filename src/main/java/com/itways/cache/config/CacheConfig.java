package com.itways.cache.config;

import com.itways.annotation.EnableCache;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(annotation = EnableCache.class)
@EnableConfigurationProperties(CacheProperties.class)
@Slf4j
@ComponentScan("com.itways.cache")
public class CacheConfig {
    @PostConstruct
    public void print() {
        log.info("✅ Common-lib Cache configuration initialized");
    }

}
