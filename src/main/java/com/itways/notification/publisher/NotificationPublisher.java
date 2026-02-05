package com.itways.notification.publisher;

import com.itways.notification.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void sendNotification(NotificationRequest request) {
        if (request.getRecipient() == null || request.getRecipient().isEmpty()) {
            throw new IllegalArgumentException("Recipient is required");
        }

        try {
            rabbitTemplate.convertAndSend(
                    NotificationRequest.EXCHANGE_NAME,
                    NotificationRequest.ROUTING_KEY,
                    request);
            log.info("Notification published to queue: {}", request.getRecipient());
        } catch (Exception e) {
            log.error("Failed to publish notification", e);
            throw e;
        }
    }
}
