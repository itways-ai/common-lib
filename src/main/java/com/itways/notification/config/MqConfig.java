package com.itways.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.itways.notification.dto.NotificationRequest;
import com.itways.notification.publisher.NotificationPublisher;

@Configuration
public class MqConfig {
	@Bean
	@ConditionalOnMissingBean
	public Queue notificationQueue() {
		return new Queue(NotificationRequest.QUEUE_NAME, true);
	}

	@Bean
	@ConditionalOnMissingBean
	public TopicExchange notificationExchange() {
		return new TopicExchange(NotificationRequest.EXCHANGE_NAME);
	}

	@Bean
	@ConditionalOnMissingBean
	public Binding notificationBinding(Queue notificationQueue, TopicExchange notificationExchange) {
		return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(NotificationRequest.ROUTING_KEY);
	}

	@Bean
	@ConditionalOnMissingBean
	public MessageConverter notificationMessageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	@ConditionalOnMissingBean
	public NotificationPublisher notificationPublisher(RabbitTemplate rabbitTemplate) {
		return new NotificationPublisher(rabbitTemplate);
	}
}
