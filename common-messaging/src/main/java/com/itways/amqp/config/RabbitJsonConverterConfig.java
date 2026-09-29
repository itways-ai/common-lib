package com.itways.amqp.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared JSON message converter for RabbitMQ producers and consumers.
 * Imported by activity and notification modules so services using either
 * annotation get Jackson serialization (not SimpleMessageConverter).
 */
@Configuration
public class RabbitJsonConverterConfig {

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter rabbitJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
