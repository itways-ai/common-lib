package com.itways.activity.outbox;

import javax.sql.DataSource;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.amqp.CachingConnectionFactoryConfigurer;
import org.springframework.boot.autoconfigure.amqp.RabbitConnectionFactoryBeanConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * The activity outbox (PLT-07), imported by {@code @EnableActivity} and active
 * only with {@code itways.activity.outbox.enabled=true}. Needs a
 * {@link DataSource}, a {@link PlatformTransactionManager} and Spring Boot's
 * RabbitMQ auto-configuration; the table comes from the service's own migration.
 */
@Configuration(value = "activityOutboxConfig", proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "itways.activity.outbox", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ActivityOutboxProperties.class)
public class ActivityOutboxConfig {

    /** The JSON of a stored row: ISO instants, unknown fields ignored (a newer writer, an older relay). */
    public static ObjectMapper outboxObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    @Bean
    public ActivityOutboxStore activityOutboxStore(DataSource dataSource, ActivityOutboxProperties properties) {
        return new ActivityOutboxStore(new JdbcTemplate(dataSource), properties.requireTable());
    }

    /**
     * On the service's own connection when it has correlated confirms and returns
     * (the platform default since PLT-30); otherwise on an extra connection named
     * {@code activity-outbox} that has them, built by Spring Boot's own configurers
     * (same addresses, credentials and TLS).
     */
    @Bean
    public RabbitConfirmedSender activityOutboxSender(RabbitTemplate rabbitTemplate,
            ObjectProvider<RabbitConnectionFactoryBeanConfigurer> beanConfigurer,
            ObjectProvider<CachingConnectionFactoryConfigurer> factoryConfigurer, ObjectProvider<AmqpAdmin> amqpAdmin,
            ActivityOutboxProperties properties) throws Exception {
        ConnectionFactory shared = rabbitTemplate.getConnectionFactory();
        if (shared.isPublisherConfirms() && shared.isPublisherReturns()) {
            return new RabbitConfirmedSender(shared, rabbitTemplate.getMessageConverter(), null, amqpAdmin,
                    properties.getConfirmTimeout());
        }
        RabbitConnectionFactoryBeanConfigurer rabbitBeanConfigurer = beanConfigurer.getIfAvailable();
        CachingConnectionFactoryConfigurer cachingConfigurer = factoryConfigurer.getIfAvailable();
        if (rabbitBeanConfigurer == null || cachingConfigurer == null) {
            throw new IllegalStateException("The activity outbox needs publisher confirms and returns: set "
                    + "spring.rabbitmq.publisher-confirm-type=correlated and spring.rabbitmq.publisher-returns=true, "
                    + "or use Spring Boot's RabbitMQ connection factory");
        }
        RabbitConnectionFactoryBean rabbitFactory = new RabbitConnectionFactoryBean();
        rabbitBeanConfigurer.configure(rabbitFactory);
        rabbitFactory.afterPropertiesSet();
        CachingConnectionFactory own = new CachingConnectionFactory(rabbitFactory.getObject());
        cachingConfigurer.configure(own);
        own.setPublisherConfirmType(ConfirmType.CORRELATED);
        own.setPublisherReturns(true);
        own.setConnectionNameStrategy(factory -> "activity-outbox");
        return new RabbitConfirmedSender(own, rabbitTemplate.getMessageConverter(), own, amqpAdmin,
                properties.getConfirmTimeout());
    }

    @Bean
    public ActivityOutboxRelay activityOutboxRelay(ActivityOutboxStore store, RabbitConfirmedSender sender,
            PlatformTransactionManager transactionManager, ActivityOutboxProperties properties) {
        return new ActivityOutboxRelay(store, sender, newTransaction(transactionManager), outboxObjectMapper(),
                properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActivityOutbox activityOutbox(ActivityOutboxStore store, ActivityOutboxRelay relay,
            PlatformTransactionManager transactionManager) {
        return new ActivityOutbox(store, outboxObjectMapper(), newTransaction(transactionManager), relay);
    }

    private static TransactionTemplate newTransaction(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /** Repeats the outer condition: component scanning of com.itways.activity registers nested classes on their own. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "itways.activity.outbox", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "io.micrometer.core.instrument.binder.MeterBinder")
    static class Metrics {

        @Bean
        ActivityOutboxMetrics activityOutboxMetrics(ActivityOutboxRelay relay) {
            return new ActivityOutboxMetrics(relay);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "itways.activity.outbox", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    static class Health {

        @Bean
        ActivityOutboxHealthIndicator activityOutboxHealthIndicator(ActivityOutboxRelay relay) {
            return new ActivityOutboxHealthIndicator(relay);
        }
    }
}
