package com.itways.activity.publisher;

import com.itways.activity.dto.AccountActivityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Slf4j
@RequiredArgsConstructor
public class ActivityEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(AccountActivityEvent event) {
        if (event == null || event.getAccountId() == null || event.getAccountId().isBlank()) {
            log.warn("Skipping activity publish: missing accountId");
            return;
        }
        try {
            rabbitTemplate.convertAndSend(
                    AccountActivityEvent.EXCHANGE_NAME,
                    AccountActivityEvent.ROUTING_KEY,
                    event);
            log.debug("Activity event published: action={}, accountId={}", event.getAction(), event.getAccountId());
        } catch (Exception e) {
            log.error("Failed to publish activity event: action={}, accountId={}",
                    event.getAction(), event.getAccountId(), e);
        }
    }
}
