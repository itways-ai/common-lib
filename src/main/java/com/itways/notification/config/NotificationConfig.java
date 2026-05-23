package com.itways.notification.config;

import com.itways.amqp.config.RabbitJsonConverterConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ComponentScan("com.itways.notification")
@Import(RabbitJsonConverterConfig.class)
public class NotificationConfig {

	@PostConstruct
	public void print() {
		log.info("✅ Common-lib notification configuration initialized");
	}
}
