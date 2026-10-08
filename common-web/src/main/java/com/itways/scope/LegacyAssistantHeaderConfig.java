package com.itways.scope;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the {@link LegacyAssistantHeaderFilter} as {@value #FILTER_NAME} on
 * {@code /*}, right after the request-id filter and ahead of the internal-endpoint
 * guard and Spring Security, so every later reader sees
 * {@link ScopeHeaders#ASSISTANT}.
 *
 * <p>
 * Part of {@code @EnableCommon} and {@code @EnableAssistantScope} (imported by
 * both, registered once). Removed with the legacy header name.
 */
@Configuration(value = "legacyAssistantHeaderConfig", proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LegacyAssistantHeaderConfig {

    public static final String FILTER_NAME = "legacyAssistantHeaderFilter";
    /** After the request-id filter ({@code HIGHEST_PRECEDENCE}), before the internal guard ({@code + 10}). */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

    @Bean
    public FilterRegistrationBean<LegacyAssistantHeaderFilter> legacyAssistantHeaderFilter() {
        FilterRegistrationBean<LegacyAssistantHeaderFilter> registration = new FilterRegistrationBean<>(
                new LegacyAssistantHeaderFilter());
        registration.setName(FILTER_NAME);
        registration.addUrlPatterns("/*");
        registration.setOrder(ORDER);
        return registration;
    }
}
