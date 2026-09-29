package com.itways.security.internal;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * What {@code @EnableInternalEndpointGuard} brings: the
 * {@link InternalEndpointGuard} filter, registered as
 * {@value #FILTER_NAME} on {@code /*} at {@link #ORDER}. Needs the
 * {@link InternalServiceToken} bean of {@code @EnableCustomSecurity}.
 *
 * <p>
 * The order is {@code Ordered.HIGHEST_PRECEDENCE + 10}, not
 * {@code HIGHEST_PRECEDENCE} as the services' own copies had: it stays ahead of
 * Spring Security (whose filter chain sits at -100) while leaving the first
 * slots free for a filter that must see every request, refused ones included,
 * such as the request-id filter ARC-25 adds at {@code HIGHEST_PRECEDENCE}.
 *
 * <p>
 * Knobs: {@code itways.internal-guard.enabled} (default {@code true}; {@code false}
 * registers nothing) and {@code itways.internal-guard.proxy-headers} (the headers
 * whose presence marks a proxied request; default {@code X-Forwarded-For,
 * X-Forwarded-Host,Forwarded}).
 */
@Configuration(value = "internalEndpointGuardConfig", proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = InternalEndpointGuardConfig.ENABLED_PROPERTY, matchIfMissing = true)
public class InternalEndpointGuardConfig {

    public static final String ENABLED_PROPERTY = "itways.internal-guard.enabled";
    public static final String PROXY_HEADERS_PROPERTY = "itways.internal-guard.proxy-headers";
    public static final String FILTER_NAME = "internalEndpointGuard";
    /** Ahead of Spring Security; {@code HIGHEST_PRECEDENCE} itself is kept for the request-id filter. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Bean
    public FilterRegistrationBean<InternalEndpointGuard> internalEndpointGuardFilter(
            InternalServiceToken internalServiceToken, ObjectProvider<ObjectMapper> objectMappers,
            @Value("${" + PROXY_HEADERS_PROPERTY + ":X-Forwarded-For,X-Forwarded-Host,Forwarded}") String proxyHeaders) {
        FilterRegistrationBean<InternalEndpointGuard> registration = new FilterRegistrationBean<>(
                new InternalEndpointGuard(internalServiceToken, objectMappers, split(proxyHeaders)));
        registration.setName(FILTER_NAME);
        registration.addUrlPatterns("/*");
        registration.setOrder(ORDER);
        return registration;
    }

    private static List<String> split(String commaSeparated) {
        return commaSeparated == null ? List.of() : Arrays.asList(commaSeparated.split(","));
    }
}
