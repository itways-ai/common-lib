package com.itways.activity.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.itways.activity.dto.AccountActivityEvent;
import com.itways.common.correlation.RequestIds;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** The direct publisher: an event without a request id is sent exactly as before ARC-25; one with it carries the header. */
class ActivityEventPublisherTest {

    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final ActivityEventPublisher publisher = new ActivityEventPublisher(template);

    @Test
    void anEventWithoutARequestIdIsSentAsBefore() {
        AccountActivityEvent event = AccountActivityEvent.builder().accountId("acc-1").action("X").build();

        publisher.publish(event);

        verify(template).convertAndSend(AccountActivityEvent.EXCHANGE_NAME, AccountActivityEvent.ROUTING_KEY, event);
        verifyNoMoreInteractions(template);
    }

    @Test
    void anEventWithARequestIdCarriesItAsTheHeader() throws Exception {
        AccountActivityEvent event = AccountActivityEvent.builder().accountId("acc-1").action("X")
                .requestId("req-pub-9").build();

        publisher.publish(event);

        ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(template).convertAndSend(eq(AccountActivityEvent.EXCHANGE_NAME), eq(AccountActivityEvent.ROUTING_KEY),
                eq((Object) event), processor.capture());
        Message message = processor.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertThat((String) message.getMessageProperties().getHeader(RequestIds.AMQP_HEADER)).isEqualTo("req-pub-9");
    }

    @Test
    void anEventWithoutAnAccountIsNotSent() {
        publisher.publish(AccountActivityEvent.builder().requestId("req").build());

        verify(template, org.mockito.Mockito.never()).convertAndSend(any(String.class), any(String.class),
                any(Object.class));
        verifyNoMoreInteractions(template);
    }
}
