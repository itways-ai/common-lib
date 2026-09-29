package com.itways.messaging.correlation;

import com.itways.common.correlation.RequestIds;
import com.rabbitmq.client.LongString;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/**
 * Puts the request id a message carries ({@value RequestIds#AMQP_HEADER}) in
 * the logging context ({@link RequestIds#MDC_KEY}) while a listener handles it
 * (ARC-25), so the listener's log lines, and the calls and messages it sends
 * on, carry the id of the request that caused the message.
 *
 * <p>
 * An advice of the listener container: it wraps the container's listener
 * invocation, whose arguments hold the {@link Message} (or, for a batch
 * listener, a {@code List<Message>}; the first message's id is used). A
 * message without the header, or with a malformed one, runs with no id: the
 * key is removed, so the id of an earlier message never leaks into this one.
 * The previous value is restored afterwards. Any other invocation passes
 * through untouched.
 *
 * <p>
 * {@code RequestIdListenerAdviceRegistrar} adds it to every listener container
 * factory bean; a service that builds a container by hand adds the
 * {@code requestIdListenerAdvice} bean to its advice chain.
 */
public class RequestIdListenerAdvice implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Message message = firstMessage(invocation.getArguments());
        if (message == null) {
            return invocation.proceed();
        }
        String previous = MDC.get(RequestIds.MDC_KEY);
        String id = requestId(message);
        if (id != null) {
            MDC.put(RequestIds.MDC_KEY, id);
        } else {
            MDC.remove(RequestIds.MDC_KEY);
        }
        try {
            return invocation.proceed();
        } finally {
            if (previous != null) {
                MDC.put(RequestIds.MDC_KEY, previous);
            } else {
                MDC.remove(RequestIds.MDC_KEY);
            }
        }
    }

    /**
     * The well-formed request id {@code message} carries, or {@code null}. The
     * header arrives as a {@code String} (or, when long or raw, as
     * {@code LongString} or {@code byte[]}).
     */
    public static String requestId(Message message) {
        if (message == null) {
            return null;
        }
        MessageProperties properties = message.getMessageProperties();
        if (properties == null) {
            return null;
        }
        Object header = properties.getHeaders().get(RequestIds.AMQP_HEADER);
        String value = null;
        if (header instanceof String text) {
            value = text;
        } else if (header instanceof byte[] bytes) {
            value = new String(bytes, StandardCharsets.UTF_8);
        } else if (header instanceof LongString longString) {
            value = new String(longString.getBytes(), StandardCharsets.UTF_8);
        }
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return RequestIds.isWellFormed(trimmed) ? trimmed : null;
    }

    /** The message of a listener invocation: a {@link Message} argument, or the first of a {@code List<Message>}. */
    static Message firstMessage(Object[] arguments) {
        if (arguments == null) {
            return null;
        }
        for (Object argument : arguments) {
            if (argument instanceof Message message) {
                return message;
            }
            if (argument instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Message message) {
                return message;
            }
        }
        return null;
    }
}
