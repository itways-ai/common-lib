package com.itways.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itways.security.servlet.ApiResponseAccessDeniedHandler;
import com.itways.security.servlet.ApiResponseAuthenticationEntryPoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * The shared 401/403 answers (PLT-14) as beans, for a service's
 * {@code SecurityFilterChain} to inject and wire into its
 * {@code exceptionHandling}. Imported by {@link SecurityConfig}, so only
 * services with {@code @EnableCustomSecurity} get them.
 *
 * <p>
 * Registering them changes no chain: Spring Security uses an entry point or a
 * denied handler only where a chain names it. Each backs off when the service
 * declares its own bean of that type.
 */
@Configuration(value = "securityErrorHandlingConfig", proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityErrorHandlingConfig {

    @Bean
    @ConditionalOnMissingBean(AuthenticationEntryPoint.class)
    public ApiResponseAuthenticationEntryPoint apiResponseAuthenticationEntryPoint(
            ObjectProvider<ObjectMapper> objectMapper) {
        return new ApiResponseAuthenticationEntryPoint(objectMapper.getIfUnique());
    }

    @Bean
    @ConditionalOnMissingBean(AccessDeniedHandler.class)
    public ApiResponseAccessDeniedHandler apiResponseAccessDeniedHandler(ObjectProvider<ObjectMapper> objectMapper) {
        return new ApiResponseAccessDeniedHandler(objectMapper.getIfUnique());
    }
}
