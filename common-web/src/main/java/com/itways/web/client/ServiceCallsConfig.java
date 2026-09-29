package com.itways.web.client;

import com.itways.feign.ForwardedAuthorizationResolver;
import com.itways.security.internal.InternalServiceToken;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The {@code serviceCalls} bean ({@link ServiceCalls}), imported by
 * {@code SecurityConfig} so every service with {@code @EnableCustomSecurity}
 * has it. Nothing is customised globally: the bean only builds clients when
 * asked. A service that declares its own {@link ServiceCalls} keeps it.
 */
@Configuration(value = "serviceCallsConfig", proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.web.client.RestClient")
public class ServiceCallsConfig {

    @Bean
    @ConditionalOnMissingBean
    public ServiceCalls serviceCalls(InternalServiceToken internalServiceToken,
            ObjectProvider<RestClient.Builder> builders, ObjectProvider<ForwardedAuthorizationResolver> fallbacks) {
        return new ServiceCalls(internalServiceToken, builders, fallbacks);
    }
}
