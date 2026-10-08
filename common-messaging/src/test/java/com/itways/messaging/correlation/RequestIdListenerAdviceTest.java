package com.itways.messaging.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.itways.common.correlation.RequestIds;
import com.rabbitmq.client.impl.LongStringHelper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.aop.framework.ProxyFactory;

/**
 * The listener advice (ARC-25), wrapped around a stand-in for the container's
 * listener invocation the way the container does it: a proxy with the advice
 * chain in front of {@code invokeListener(channel, data)}.
 */
class RequestIdListenerAdviceTest {

    /** The shape of the container's {@code ContainerDelegate}. */
    interface Delegate {
        void invokeListener(Object channel, Object data);
    }

    /** The logging context each listener call saw. */
    private final List<String> seen = new ArrayList<>();

    private Delegate listener(RuntimeException failure) {
        ProxyFactory factory = new ProxyFactory((Delegate) (channel, data) -> {
            seen.add(MDC.get(RequestIds.MDC_KEY));
            if (failure != null) {
                throw failure;
            }
        });
        factory.addInterface(Delegate.class);
        factory.addAdvice(new RequestIdListenerAdvice());
        return (Delegate) factory.getProxy();
    }

    @AfterEach
    void cleanThread() {
        MDC.clear();
    }

    private static Message message(Object header) {
        MessageProperties properties = new MessageProperties();
        if (header != null) {
            properties.setHeader(RequestIds.AMQP_HEADER, header);
        }
        return new Message("{}".getBytes(), properties);
    }

    @Test
    void theListenerRunsWithTheIdOfItsMessage() {
        listener(null).invokeListener(null, message("req-msg-1"));

        assertThat(seen).containsExactly("req-msg-1");
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    @Test
    void theHeaderMayArriveAsBytesOrALongString() {
        Delegate listener = listener(null);

        listener.invokeListener(null, message("req-bytes".getBytes(StandardCharsets.UTF_8)));
        listener.invokeListener(null, message(LongStringHelper.asLongString("req-long")));

        assertThat(seen).containsExactly("req-bytes", "req-long");
    }

    @Test
    void aBatchUsesItsFirstMessage() {
        listener(null).invokeListener(null, List.of(message("req-first"), message("req-second")));

        assertThat(seen).containsExactly("req-first");
    }

    @Test
    void aMessageWithoutAnIdRunsWithNoneAndTheStaleOneComesBackAfterwards() {
        MDC.put(RequestIds.MDC_KEY, "stale");
        Delegate listener = listener(null);

        listener.invokeListener(null, message(null));
        listener.invokeListener(null, message("not well formed"));
        listener.invokeListener(null, message(42));

        assertThat(seen).containsExactly(null, null, null);
        assertThat(MDC.get(RequestIds.MDC_KEY)).isEqualTo("stale");
    }

    @Test
    void theContextIsRestoredWhenTheListenerFails() {
        assertThatThrownBy(() -> listener(new IllegalStateException("boom")).invokeListener(null,
                message("req-fail"))).isInstanceOf(IllegalStateException.class);

        assertThat(seen).containsExactly("req-fail");
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    @Test
    void anythingButAListenerInvocationPassesThrough() {
        MDC.put(RequestIds.MDC_KEY, "outer");

        listener(null).invokeListener(null, "not a message");
        listener(null).invokeListener(null, List.of());

        assertThat(seen).containsExactly("outer", "outer");
    }

    @Test
    void theHeaderIsReadTrimmedAndCheckedOnItsOwnToo() {
        assertThat(RequestIdListenerAdvice.requestId(message(" req-trim "))).isEqualTo("req-trim");
        assertThat(RequestIdListenerAdvice.requestId(message("x".repeat(65)))).isNull();
        assertThat(RequestIdListenerAdvice.requestId(null)).isNull();
    }
}
