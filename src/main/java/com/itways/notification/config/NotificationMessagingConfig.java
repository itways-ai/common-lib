package com.itways.notification.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ComponentScan("com.itways.notification")
public class NotificationMessagingConfig {

	@PostConstruct
	public void print() {
		log.info("✅ Common-lib notification configuration initialized");
	}
}
