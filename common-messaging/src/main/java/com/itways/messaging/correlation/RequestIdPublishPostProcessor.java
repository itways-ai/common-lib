package com.itways.messaging.correlation;

import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;

import com.itways.common.correlation.RequestIds;

/**
 * Puts the request id of the work being done on this thread (the logging
 * context's {@link RequestIds#MDC_KEY}: a request, or a message being handled)
 * on every message a {@code RabbitTemplate} publishes, as the
 * {@value RequestIds#AMQP_HEADER} header (ARC-25). A message that already has
 * the header keeps it; with no id in the logging context nothing is added.
 *
 * <p>
 * Registered on Spring Boot's template by the {@code requestIdPublishing}
 * customizer of {@code RabbitPublishingAutoConfiguration}; a template built by
 * hand adds it with {@code addBeforePublishPostProcessors(...)}.
 */
public class RequestIdPublishPostProcessor implements MessagePostProcessor {

    @Override
    public Message postProcessMessage(Message message) {
        MessageProperties properties = message.getMessageProperties();
        if (properties != null && properties.getHeader(RequestIds.AMQP_HEADER) == null) {
            String id = MDC.get(RequestIds.MDC_KEY);
            if (RequestIds.isWellFormed(id)) {
                properties.setHeader(RequestIds.AMQP_HEADER, id);
            }
        }
        return message;
    }
}
