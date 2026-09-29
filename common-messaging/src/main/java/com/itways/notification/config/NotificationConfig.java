package com.itways.notification.config;

import com.itways.amqp.config.RabbitJsonConverterConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** What {@code @EnableNotifications} brings: the converter and {@link MqConfig}, which declares the {@code notificationPublisher} bean (it used to component-scan {@code com.itways.notification}). */
@Slf4j
@Configuration
@Import({ RabbitJsonConverterConfig.class, MqConfig.class })
public class NotificationConfig {

    @PostConstruct
    public void print() {
        log.info("✅ Common-lib notification configuration initialized");
    }
}
