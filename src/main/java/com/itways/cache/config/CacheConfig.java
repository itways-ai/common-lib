package com.itways.cache.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CacheProperties.class)
@Slf4j
@ComponentScan("com.itways.cache")
public class CacheConfig {
    @PostConstruct
    public void print() {
        log.info("✅ Common-lib Cache configuration initialized");
    }

}
