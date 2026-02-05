package com.itways.common.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ComponentScan("com.itways.common")
public class CommonModuleConfiguration {
	
	@PostConstruct
	public void print() {
		log.info("✅ Common-lib shared common configuration initialized");
	}
}
