package com.itways.security.config;

import com.itways.security.ApiKeyProvider;
import com.itways.security.ApiKeyStatusStore;
import com.itways.security.SecurityUtils;
import com.itways.security.SessionRevocationStore;
import com.itways.security.internal.InternalServiceToken;
import com.itways.security.internal.ServiceTokenAuthenticationConfig;
import com.itways.security.jwt.JwtTokenProvider;
import com.itways.security.servlet.ApiKeyAuthenticationFilter;
import com.itways.security.servlet.ClientIpResolver;
import com.itways.security.servlet.JwtAuthenticationFilter;
import com.itways.web.client.ServiceCallsConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * What {@code @EnableCustomSecurity} brings. Listed explicitly (it used to
 * component-scan {@code com.itways.security}); the bean names are the ones the
 * scan gave. {@link ClientIpResolver} and {@link ServiceCallsConfig} (ARC-11)
 * are plain helpers with no side effects: a bean each, nothing switched on.
 * {@link ServiceTokenAuthenticationConfig} (2.2.0) likewise only offers the
 * service-token filter, when {@code itways.internal-token} is set, for a
 * service's chain to add.
 */
@Slf4j
@Configuration
@Import({ SecurityUtils.class, ApiKeyProvider.class, ApiKeyStatusStore.class, SessionRevocationStore.class,
        JwtTokenProvider.class, InternalServiceToken.class, JwtAuthenticationFilter.class,
        ApiKeyAuthenticationFilter.class, AccountIdWebMvcConfig.class, SecurityErrorHandlingConfig.class,
        ClientIpResolver.class, ServiceCallsConfig.class, ServiceTokenAuthenticationConfig.class })
public class SecurityConfig {

    @PostConstruct
    public void print() {
        log.info("✅ Common-lib security configuration initialized");
    }
}
