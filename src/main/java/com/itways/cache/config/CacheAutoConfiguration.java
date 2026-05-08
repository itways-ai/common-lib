package com.itways.cache.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@EnableConfigurationProperties(CacheProperties.class)
@ComponentScan(basePackages = "com.itways.cache")
public class CacheAutoConfiguration {
}
