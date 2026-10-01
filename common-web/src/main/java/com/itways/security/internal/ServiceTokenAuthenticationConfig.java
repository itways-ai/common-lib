package com.itways.security.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Registers {@link ServiceTokenAuthenticationFilter} (2.2.0) as the bean
 * {@value #FILTER_BEAN_NAME}, for a service's {@code SecurityFilterChain} to add. Imported by
 * {@code @EnableCustomSecurity}; active only when {@value #TOKEN_PROPERTY} is set to a non-blank
 * value, so a service without the token has no such bean (inject it as
 * {@code ObjectProvider<ServiceTokenAuthenticationFilter>} and add it when present).
 *
 * <p>
 * The filter is a {@code Filter} bean, which Spring Boot would also run in the servlet
 * container's chain, after Spring Security has decided. The disabled registration
 * {@value #REGISTRATION_BEAN_NAME} prevents that: it runs only where a service's chain puts it.
 */
@Configuration(value = "serviceTokenAuthenticationConfig", proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Conditional(ServiceTokenAuthenticationConfig.TokenConfigured.class)
public class ServiceTokenAuthenticationConfig {

    public static final String TOKEN_PROPERTY = "itways.internal-token";
    public static final String FILTER_BEAN_NAME = "serviceTokenAuthenticationFilter";
    public static final String REGISTRATION_BEAN_NAME = "serviceTokenAuthenticationFilterRegistration";

    @Bean(FILTER_BEAN_NAME)
    public ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter(InternalServiceToken internalServiceToken) {
        return new ServiceTokenAuthenticationFilter(internalServiceToken);
    }

    @Bean(REGISTRATION_BEAN_NAME)
    public FilterRegistrationBean<ServiceTokenAuthenticationFilter> serviceTokenAuthenticationFilterRegistration(
            ServiceTokenAuthenticationFilter filter) {
        FilterRegistrationBean<ServiceTokenAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** {@value #TOKEN_PROPERTY} is set and not blank (an empty {@code ${INTERNAL_SERVICE_TOKEN:}} is not set). */
    static class TokenConfigured implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty(TOKEN_PROPERTY));
        }
    }
}
