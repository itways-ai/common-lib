package com.itways.messaging;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Publisher confirms and returns for every service with common-lib (PLT-30).
 *
 * <p>
 * Before this no service set {@code spring.rabbitmq.publisher-confirm-type}: a
 * message the broker refused (a nack) or could not route anywhere was dropped
 * without a trace, and the activity outbox relay had to open a second AMQP
 * connection of its own to get confirms. These defaults turn on
 * {@code CORRELATED} confirms and returns on the services' shared connection
 * factory, and make the {@code RabbitTemplate} publish {@code mandatory}, so
 * the broker hands an unroutable message back instead of discarding it.
 * {@link RabbitPublishingAutoConfiguration} logs both outcomes for publishers
 * that do not wait for them; the outbox relay waits and keeps its rows.
 *
 * <p>
 * Added as the last property source, so a service's own
 * {@code application.properties} or environment still wins.
 * Registered in {@code META-INF/spring.factories}.
 */
public class RabbitPublishingDefaults implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "itwaysRabbitPublishingDefaults";

    static final Map<String, Object> DEFAULTS;

    static {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("spring.rabbitmq.publisher-confirm-type", "correlated");
        defaults.put("spring.rabbitmq.publisher-returns", "true");
        defaults.put("spring.rabbitmq.template.mandatory", "true");
        DEFAULTS = Map.copyOf(defaults);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getPropertySources().contains(SOURCE_NAME)) {
            environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, DEFAULTS));
        }
    }
}
