package com.itways.security.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.itways.security.resolver.AccountIdArgumentResolver;

/**
 * Registers {@link AccountIdArgumentResolver} so controllers can take an
 * {@code @AccountId} parameter.
 *
 * Picked up by the {@code @ComponentScan("com.itways.security")} on
 * {@link SecurityConfig}, so any service using {@code @EnableCustomSecurity} gets
 * it. Five services previously carried a byte-identical local {@code WebConfig}
 * doing exactly this, while the resolver it registers already lived here.
 *
 * Servlet-only: api-gateway is a WebFlux application and depends on this library,
 * though it does not enable custom security today.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AccountIdWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AccountIdArgumentResolver());
    }
}
