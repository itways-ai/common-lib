package com.itways.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.itways.notification.dto.NotificationRequest;
import com.itways.notification.publisher.NotificationPublisher;

@Configuration
public class MqConfig {

	@Bean("notificationQueue")
	@ConditionalOnMissingBean(name = "notificationQueue")
	public Queue notificationQueue() {
		return new Queue(NotificationRequest.QUEUE_NAME, true);
	}

	@Bean("notificationExchange")
	@ConditionalOnMissingBean(name = "notificationExchange")
	public TopicExchange notificationExchange() {
		return new TopicExchange(NotificationRequest.EXCHANGE_NAME);
	}

	@Bean("notificationBinding")
	@ConditionalOnMissingBean(name = "notificationBinding")
	public Binding notificationBinding(
			@Qualifier("notificationQueue") Queue notificationQueue,
			@Qualifier("notificationExchange") TopicExchange notificationExchange) {
		return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(NotificationRequest.ROUTING_KEY);
	}

	@Bean
	@ConditionalOnMissingBean
	public NotificationPublisher notificationPublisher(RabbitTemplate rabbitTemplate) {
		return new NotificationPublisher(rabbitTemplate);
	}
}
