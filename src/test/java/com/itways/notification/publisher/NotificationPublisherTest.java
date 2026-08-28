package com.itways.notification.publisher;

import com.itways.notification.dto.NotificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * NotificationPublisher is the opposite temperament to ActivityEventPublisher,
 * and the contrast is the contract: a notification the caller asked for MUST
 * reach the queue, so a missing recipient is an IllegalArgumentException and a
 * broker failure propagates instead of being swallowed. Callers rely on that
 * failure to retry or surface the error.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationPublisher")
class NotificationPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private NotificationPublisher publisher() {
        return new NotificationPublisher(rabbitTemplate);
    }

    private static NotificationRequest requestFor(String recipient) {
        return NotificationRequest.builder()
                .recipient(recipient)
                .subject("Welcome")
                .body("Hello")
                .type("EMAIL")
                .build();
    }

    @Test
    @DisplayName("a valid request goes to the notification exchange with its routing key")
    void validRequestPublished() {
        NotificationRequest request = requestFor("user@example.com");

        publisher().sendNotification(request);

        verify(rabbitTemplate).convertAndSend(
                "notification.exchange", "notification.routing.key", request);
    }

    @Test
    @DisplayName("a missing recipient is rejected up front with IllegalArgumentException")
    void missingRecipientRejected() {
        assertThatThrownBy(() -> publisher().sendNotification(requestFor(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Recipient is required");
        assertThatThrownBy(() -> publisher().sendNotification(requestFor("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Recipient is required");

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("a whitespace-only recipient slips past validation and is published")
    void whitespaceRecipientPublished() {
        // NOTE: possible defect — the guard uses isEmpty(), not isBlank(), so
        // "   " counts as a recipient and an undeliverable message reaches the
        // queue. ActivityEventPublisher's guard uses isBlank(); the two
        // publishers disagree. Pinned as current behavior.
        NotificationRequest request = requestFor("   ");

        publisher().sendNotification(request);

        verify(rabbitTemplate).convertAndSend(
                "notification.exchange", "notification.routing.key", request);
    }

    @Test
    @DisplayName("a broker failure propagates to the caller instead of being swallowed")
    void brokerFailurePropagates() {
        AmqpException failure = new AmqpException("broker down");
        doThrow(failure)
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatThrownBy(() -> publisher().sendNotification(requestFor("user@example.com")))
                .isSameAs(failure);
    }
}
