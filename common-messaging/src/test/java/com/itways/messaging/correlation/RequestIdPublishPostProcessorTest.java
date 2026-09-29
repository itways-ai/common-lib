package com.itways.messaging.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import com.itways.common.correlation.RequestIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class RequestIdPublishPostProcessorTest {

    private final RequestIdPublishPostProcessor processor = new RequestIdPublishPostProcessor();

    @AfterEach
    void cleanThread() {
        MDC.clear();
    }

    private static Message message() {
        return new Message("{}".getBytes(), new MessageProperties());
    }

    @Test
    void putsTheIdOfTheLoggingContextOnTheMessage() {
        MDC.put(RequestIds.MDC_KEY, "req-pub-1");

        Message processed = processor.postProcessMessage(message());

        assertThat((String) processed.getMessageProperties().getHeader("x-request-id")).isEqualTo("req-pub-1");
    }

    @Test
    void neverOverwritesAnIdTheMessageAlreadyHas() {
        MDC.put(RequestIds.MDC_KEY, "req-pub-2");
        Message message = message();
        message.getMessageProperties().setHeader(RequestIds.AMQP_HEADER, "from-the-event");

        processor.postProcessMessage(message);

        assertThat((String) message.getMessageProperties().getHeader(RequestIds.AMQP_HEADER))
                .isEqualTo("from-the-event");
    }

    @Test
    void addsNothingWithoutAWellFormedId() {
        assertThat(processor.postProcessMessage(message()).getMessageProperties().getHeaders())
                .doesNotContainKey(RequestIds.AMQP_HEADER);

        MDC.put(RequestIds.MDC_KEY, "not well formed");
        assertThat(processor.postProcessMessage(message()).getMessageProperties().getHeaders())
                .doesNotContainKey(RequestIds.AMQP_HEADER);
    }
}
