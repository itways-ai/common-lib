package com.itways.activity.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import com.itways.amqp.config.RabbitJsonConverterConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Slf4j
@Configuration
@ComponentScan("com.itways.activity")
@Import({RabbitJsonConverterConfig.class, ActivityMqConfig.class})
public class ActivityConfig {

    @PostConstruct
    public void init() {
        log.info("Common-lib activity configuration initialized");
    }
}
