package com.itways.security.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.itways.security.ApiKeyProvider;
import com.itways.security.ApiKeyRevocationStore;
import com.itways.security.ApiKeyStatusStore;
import com.itways.security.SecurityUtils;
import com.itways.security.SessionRevocationStore;
import com.itways.security.internal.InternalServiceToken;
import com.itways.security.jwt.JwtTokenProvider;
import com.itways.security.servlet.ApiKeyAuthenticationFilter;
import com.itways.security.servlet.JwtAuthenticationFilter;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * What {@code @EnableCustomSecurity} brings. Listed explicitly (it used to
 * component-scan {@code com.itways.security}); the bean names are the ones the
 * scan gave.
 */
@Slf4j
@Configuration
@SuppressWarnings("removal") // ApiKeyRevocationStore stays until its last consumer is gone
@Import({ SecurityUtils.class, ApiKeyProvider.class, ApiKeyStatusStore.class, ApiKeyRevocationStore.class,
		SessionRevocationStore.class, JwtTokenProvider.class, InternalServiceToken.class,
		JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class, AccountIdWebMvcConfig.class,
		SecurityErrorHandlingConfig.class })
public class SecurityConfig {
	
	@PostConstruct
	public void print() {
		log.info("✅ Common-lib security configuration initialized");
	}
}
