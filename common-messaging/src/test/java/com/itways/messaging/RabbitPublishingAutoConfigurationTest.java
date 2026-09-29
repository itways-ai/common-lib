package com.itways.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.expression.Expression;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PLT-30: every service's RabbitMQ connection has correlated confirms and
 * returns, its template publishes mandatory, and nacks and returns are logged.
 * Nothing here connects to a broker.
 */
class RabbitPublishingAutoConfigurationTest {

    /** A Spring Boot application runs the defaults as an EnvironmentPostProcessor; the runner does not. */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> new RabbitPublishingDefaults().postProcessEnvironment(context.getEnvironment(),
                    null))
            .withConfiguration(
                    AutoConfigurations.of(RabbitPublishingAutoConfiguration.class, RabbitAutoConfiguration.class));

    @Test
    void confirmsReturnsAndMandatoryAreOnByDefault() {
        runner.run(context -> {
            CachingConnectionFactory factory = context.getBean(CachingConnectionFactory.class);
            assertThat(factory.isPublisherConfirms()).isTrue();
            assertThat(factory.isSimplePublisherConfirms()).isFalse();
            assertThat(factory.isPublisherReturns()).isTrue();

            RabbitTemplate template = context.getBean(RabbitTemplate.class);
            Expression mandatory = (Expression) ReflectionTestUtils.getField(template, "mandatoryExpression");
            assertThat(mandatory.getValue()).isEqualTo(true);
            assertThat(ReflectionTestUtils.getField(template, "confirmCallback")).isNotNull();
            assertThat(ReflectionTestUtils.getField(template, "returnsCallback")).isNotNull();
        });
    }

    @Test
    void aServiceOwnSettingWins() {
        runner.withPropertyValues("spring.rabbitmq.publisher-confirm-type=none",
                "spring.rabbitmq.publisher-returns=false", "spring.rabbitmq.template.mandatory=false").run(context -> {
                    CachingConnectionFactory factory = context.getBean(CachingConnectionFactory.class);
                    assertThat(factory.isPublisherConfirms()).isFalse();
                    assertThat(factory.isPublisherReturns()).isFalse();
                    Expression mandatory = (Expression) ReflectionTestUtils
                            .getField(context.getBean(RabbitTemplate.class), "mandatoryExpression");
                    assertThat(mandatory.getValue()).isEqualTo(false);
                });
    }

    @Test
    @SuppressWarnings("deprecation")
    void theDefaultsAreRegisteredForEveryService() {
        assertThat(SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class, getClass().getClassLoader()))
                .contains(RabbitPublishingDefaults.class.getName());
    }
}
