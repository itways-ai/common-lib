package com.itways.web.correlation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import jakarta.servlet.DispatcherType;

/**
 * Request correlation for a servlet service (ARC-25): the
 * {@link RequestIdFilter}, registered as {@value #FILTER_NAME} on {@code /*}
 * at {@link Ordered#HIGHEST_PRECEDENCE}, ahead of the internal-endpoint guard
 * ({@code HIGHEST_PRECEDENCE + 10}) and Spring Security (-100), so even a
 * refused request has an id in its logs, its response header and its error
 * body. It runs on request, async and error dispatches.
 *
 * <p>
 * Part of {@code @EnableCommon}; {@code @EnableRequestCorrelation} imports it
 * alone. {@value #ENABLED_PROPERTY}{@code =false} (default {@code true})
 * registers nothing.
 */
@Configuration(value = "requestCorrelationConfig", proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = RequestCorrelationConfig.ENABLED_PROPERTY, matchIfMissing = true)
public class RequestCorrelationConfig {

    public static final String ENABLED_PROPERTY = "itways.request-id.enabled";
    public static final String FILTER_NAME = "requestIdFilter";
    /** First of all filters: {@code InternalEndpointGuardConfig.ORDER} leaves this slot free for it. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setName(FILTER_NAME);
        registration.addUrlPatterns("/*");
        registration.setOrder(ORDER);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        return registration;
    }
}
