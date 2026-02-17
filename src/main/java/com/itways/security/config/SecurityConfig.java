package com.itways.security.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ComponentScan("com.itways.security")
public class SecurityConfig {
	
	@PostConstruct
	public void print() {
		log.info("✅ Common-lib security configuration initialized");
	}
}
