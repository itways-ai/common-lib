package com.itways.activity.publisher;

import com.itways.activity.dto.AccountActivityEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ActivityEventPublisher feeds the account activity timeline. Its contract is
 * deliberately best-effort: an event without a tenant (accountId) is dropped,
 * and a broker outage is swallowed — activity logging must never take down the
 * business operation that emitted the event. The exchange and routing key are
 * pinned because the consumer side binds to the same constants.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityEventPublisher")
class ActivityEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ActivityEventPublisher publisher() {
        return new ActivityEventPublisher(rabbitTemplate);
    }

    private static AccountActivityEvent eventFor(String accountId) {
        return AccountActivityEvent.builder()
                .accountId(accountId)
                .action("JOURNEY_PUBLISHED")
                .build();
    }

    @Test
    @DisplayName("a well-formed event goes to the activity exchange with the record routing key")
    void wellFormedEventPublished() {
        AccountActivityEvent event = eventFor("AIUS000000000001");

        publisher().publish(event);

        verify(rabbitTemplate).convertAndSend(
                "account.activity.exchange", "account.activity.record", event);
    }

    @Test
    @DisplayName("a null event is dropped without touching the broker")
    void nullEventDropped() {
        publisher().publish(null);

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("an event without an accountId is dropped — no tenant, no timeline entry")
    void missingAccountIdDropped() {
        // An accountId-less event would land in nobody's timeline (or worse,
        // a shared one) — the guard drops it at the source instead.
        publisher().publish(eventFor(null));
        publisher().publish(eventFor("   "));

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("a broker failure is swallowed — activity logging never fails the caller")
    void brokerFailureSwallowed() {
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> publisher().publish(eventFor("AIUS000000000001")))
                .doesNotThrowAnyException();
    }
}
