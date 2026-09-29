package com.itways.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * The {@code @Enable*} configurations import their classes explicitly instead of
 * component-scanning their packages (ARC-10). A class registered through
 * {@code @Import} would get its fully-qualified name as bean name, so every
 * formerly scanned class names itself: this pins the names a service may refer
 * to, and the two beans that went on purpose ({@code restTemplate},
 * {@code refGenerator}).
 */
class EnableAnnotationsBeanNamesTest {

    @Test
    void theFormerlyScannedBeansKeepTheirNames() {
        new WebApplicationContextRunner().withUserConfiguration(App.class)
                .withPropertyValues("jwt.encryption.key=test-only-key", "jwt.rsa.public-key=" + publicKey())
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    // @EnableCustomSecurity
                    assertThat(context).hasBean("securityUtils").hasBean("apiKeyProvider").hasBean("apiKeyStatusStore")
                            .hasBean("apiKeyRevocationStore").hasBean("sessionRevocationStore")
                            .hasBean("jwtTokenProvider").hasBean("internalServiceToken")
                            .hasBean("jwtAuthenticationFilter").hasBean("apiKeyAuthenticationFilter")
                            .hasBean("accountIdWebMvcConfig").hasBean("securityErrorHandlingConfig")
                            .hasBean("apiResponseAuthenticationEntryPoint").hasBean("apiResponseAccessDeniedHandler");

                    // @EnableCache, through @EnableCustomSecurity
                    assertThat(context).hasBean("cacheConfig").hasBean("cacheProviderConfig")
                            .hasBean("redisHealthChecker").hasBean("cacheStoreFactory").hasBean("cacheManager");

                    // @EnableCommon
                    assertThat(context).hasBean("globalExceptionHandler").hasBean("dataAccessExceptionHandler")
                            .hasBean("customErrorController").hasBean("swaggerConfig").hasBean("timeConfig");

                    // @EnableEncryption
                    assertThat(context).hasBean("rsaService");

                    // Gone on purpose: a service that needs a RestTemplate declares its own; RefGenerator had no user.
                    assertThat(context).doesNotHaveBean("restTemplate").doesNotHaveBean("refGenerator");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCommon
    @EnableCustomSecurity
    @EnableEncryption
    static class App {

        /** Stands in for Spring Boot's Redis auto-configuration, which the runner does not load. */
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }

    private static String publicKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
