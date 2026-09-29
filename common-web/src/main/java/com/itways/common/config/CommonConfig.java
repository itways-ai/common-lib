package com.itways.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.itways.common.handler.CustomErrorController;
import com.itways.common.handler.DataAccessExceptionHandler;
import com.itways.common.handler.GlobalExceptionHandler;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * What {@code @EnableCommon} brings: the shared error handling and the
 * OpenAPI and time configuration. Listed explicitly (it used to component-scan
 * {@code com.itways.common}); the bean names are the ones the scan gave.
 */
@Slf4j
@Configuration
@Import({ GlobalExceptionHandler.class, DataAccessExceptionHandler.class, CustomErrorController.class,
		SwaggerConfig.class, TimeConfig.class })
public class CommonConfig {
	
	@PostConstruct
	public void print() {
		log.info("✅ Common-lib shared common configuration initialized");
	}
}
