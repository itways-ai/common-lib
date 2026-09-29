package com.itways.activity.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import com.itways.activity.outbox.ActivityOutboxConfig;
import com.itways.amqp.config.RabbitJsonConverterConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** What {@code @EnableActivity} brings; the imports are the whole list (it used to component-scan {@code com.itways.activity} as well). */
@Slf4j
@Configuration
@Import({RabbitJsonConverterConfig.class, ActivityMqConfig.class, ActivityOutboxConfig.class})
public class ActivityConfig {

    @PostConstruct
    public void init() {
        log.info("Common-lib activity configuration initialized");
    }
}
