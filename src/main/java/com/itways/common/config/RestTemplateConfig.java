package com.itways.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * A plain {@link RestTemplate} for services that make outbound HTTP calls.
 *
 * Picked up by the {@code @ComponentScan("com.itways.common")} on
 * {@link CommonConfig}, so any service using {@code @EnableCommon} gets one.
 * Three services previously declared a byte-identical local bean; the bean name
 * here is deliberately {@code restTemplate}, matching those, because injection
 * points resolve it by name.
 *
 * The condition matches on the bean <em>name</em>, not the type, and that matters:
 * ai-engine-sdk contributes an {@code aiRestTemplate} which trusts all TLS
 * certificates so AI provider calls survive corporate proxies. A type-based
 * {@code @ConditionalOnMissingBean} would see that bean, back off, and leave the
 * only RestTemplate in the context a certificate-ignoring one — which
 * speech-service's Telegram and Twilio senders would then pick up by type.
 *
 * A service needing its own transport should declare a bean named
 * {@code restTemplate}; this one then steps aside. Note the back-off is only
 * dependable against beans registered before this scan runs — condition ordering
 * inside component-scanned configuration is not guaranteed the way it is in a
 * real auto-configuration.
 */
@Configuration
@ConditionalOnClass(RestTemplate.class)
public class RestTemplateConfig {

    @Bean
    @ConditionalOnMissingBean(name = "restTemplate")
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
