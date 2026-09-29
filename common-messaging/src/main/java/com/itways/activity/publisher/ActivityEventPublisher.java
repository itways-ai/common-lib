package com.itways.activity.publisher;

import com.itways.activity.dto.AccountActivityEvent;
import com.itways.common.correlation.RequestIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Sends an activity event to RabbitMQ at once (the outbox's alternative).
 *
 * <p>
 * An event with a {@code requestId} goes with it as the {@code x-request-id}
 * header (ARC-25); one without is sent exactly as before, and the service
 * template's {@code requestIdPublishing} post-processor adds the header from
 * the logging context when there is a request id.
 */
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
            String requestId = event.getRequestId();
            if (RequestIds.isWellFormed(requestId)) {
                rabbitTemplate.convertAndSend(
                        AccountActivityEvent.EXCHANGE_NAME,
                        AccountActivityEvent.ROUTING_KEY,
                        event,
                        message -> {
                            message.getMessageProperties().setHeader(RequestIds.AMQP_HEADER, requestId);
                            return message;
                        });
            } else {
                rabbitTemplate.convertAndSend(
                        AccountActivityEvent.EXCHANGE_NAME,
                        AccountActivityEvent.ROUTING_KEY,
                        event);
            }
            log.debug("Activity event published: action={}, accountId={}", event.getAction(), event.getAccountId());
        } catch (Exception e) {
            log.error("Failed to publish activity event: action={}, accountId={}",
                    event.getAction(), event.getAccountId(), e);
        }
    }
}
