package com.itways.activity.config;

import com.itways.activity.dto.AccountActivityEvent;
import com.itways.activity.publisher.ActivityEventPublisher;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActivityMqConfig {

    @Bean("accountActivityQueue")
    @ConditionalOnMissingBean(name = "accountActivityQueue")
    public Queue accountActivityQueue() {
        return new Queue(AccountActivityEvent.QUEUE_NAME, true);
    }

    @Bean("accountActivityExchange")
    @ConditionalOnMissingBean(name = "accountActivityExchange")
    public TopicExchange accountActivityExchange() {
        return new TopicExchange(AccountActivityEvent.EXCHANGE_NAME);
    }

    @Bean("accountActivityBinding")
    @ConditionalOnMissingBean(name = "accountActivityBinding")
    public Binding accountActivityBinding(
            @Qualifier("accountActivityQueue") Queue accountActivityQueue,
            @Qualifier("accountActivityExchange") TopicExchange accountActivityExchange) {
        return BindingBuilder.bind(accountActivityQueue)
                .to(accountActivityExchange)
                .with(AccountActivityEvent.ROUTING_KEY);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActivityEventPublisher activityEventPublisher(RabbitTemplate rabbitTemplate) {
        return new ActivityEventPublisher(rabbitTemplate);
    }
}
