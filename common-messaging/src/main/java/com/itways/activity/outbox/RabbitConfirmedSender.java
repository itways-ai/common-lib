package com.itways.activity.outbox;

import com.itways.activity.dto.AccountActivityEvent;
import com.itways.common.correlation.RequestIds;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Publishes a batch with correlated publisher confirms and returns, and waits
 * until the broker has acked every message and routed it to a queue.
 *
 * <p>
 * Since PLT-30 every service's connection factory has {@code CORRELATED}
 * confirms and returns (common-lib's {@code RabbitPublishingDefaults}), so the
 * sender publishes on that shared connection; before, it opened an extra
 * {@code activity-outbox} connection per instance. It still does when a service
 * turned confirms or returns off ({@code ActivityOutboxConfig}).
 *
 * <p>
 * The sender has its own {@link RabbitTemplate} on that connection factory,
 * {@code mandatory}, with the service template's message converter: its
 * callbacks and flags do not touch the service's template. Each message carries
 * a {@link CorrelationData} with the event id, and the event's
 * {@code requestId} as the {@code x-request-id} header when it has one (ARC-25).
 * A nack, a return (no queue bound,
 * e.g. {@code account.activity.queue} deleted), a timeout or a closed channel
 * fails the whole batch; the relay keeps the rows and tries again later
 * ({@link ActivityBatchSender}).
 *
 * <p>
 * The exchange, queue and binding are declared by the service's {@link AmqpAdmin}
 * (from {@code ActivityMqConfig}) before the first batch and again after a
 * failure, so a deleted exchange or queue comes back on the next attempt.
 */
@Slf4j
public class RabbitConfirmedSender implements ActivityBatchSender, DisposableBean {

    private final RabbitTemplate template;
    private final CachingConnectionFactory ownFactory;
    private final ObjectProvider<AmqpAdmin> amqpAdmin;
    private final long confirmTimeoutMillis;
    private volatile boolean declared;

    /**
     * @param connectionFactory a factory with correlated publisher confirms and returns
     * @param converter         the service template's converter: the wire format stays the direct publisher's
     * @param ownFactory        the factory created for this sender, closed with it; null when shared
     */
    public RabbitConfirmedSender(ConnectionFactory connectionFactory, MessageConverter converter,
            CachingConnectionFactory ownFactory, ObjectProvider<AmqpAdmin> amqpAdmin, Duration confirmTimeout) {
        RabbitTemplate confirming = new RabbitTemplate(connectionFactory);
        confirming.setMessageConverter(converter);
        confirming.setMandatory(true);
        confirming.setReturnsCallback(returned -> log.warn(
                "[ACTIVITY] Event {} was returned unrouted by {} / {}: {} {}; the outbox keeps it",
                returned.getMessage().getMessageProperties().getMessageId(), returned.getExchange(),
                returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText()));
        this.template = confirming;
        this.ownFactory = ownFactory;
        this.amqpAdmin = amqpAdmin;
        this.confirmTimeoutMillis = confirmTimeout.toMillis();
    }

    /** Whether the sender publishes on the service's own connection (no extra {@code activity-outbox} one). */
    public boolean sharesTheServiceConnection() {
        return ownFactory == null;
    }

    @Override
    public void send(List<AccountActivityEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        declareOnce();
        try {
            List<CorrelationData> pending = new ArrayList<>(events.size());
            for (AccountActivityEvent event : events) {
                String id = String.valueOf(event.getEventId());
                String requestId = event.getRequestId();
                CorrelationData correlation = new CorrelationData(id);
                template.convertAndSend(AccountActivityEvent.EXCHANGE_NAME, AccountActivityEvent.ROUTING_KEY, event,
                        message -> {
                            message.getMessageProperties().setMessageId(id);
                            if (RequestIds.isWellFormed(requestId)) {
                                message.getMessageProperties().setHeader(RequestIds.AMQP_HEADER, requestId);
                            }
                            return message;
                        }, correlation);
                pending.add(correlation);
            }
            awaitConfirms(pending);
        } catch (RuntimeException e) {
            declared = false;
            throw e;
        }
    }

    /** Every message acked and none returned, within the timeout; otherwise the batch fails. */
    private void awaitConfirms(List<CorrelationData> pending) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(confirmTimeoutMillis);
        int nacked = 0;
        int returned = 0;
        String reason = null;
        for (CorrelationData correlation : pending) {
            CorrelationData.Confirm confirm = await(correlation, deadline);
            ReturnedMessage returnedMessage = correlation.getReturned();
            if (returnedMessage != null) {
                returned++;
                reason = "returned " + returnedMessage.getReplyCode() + " " + returnedMessage.getReplyText();
            } else if (!confirm.isAck()) {
                nacked++;
                reason = "nack " + confirm.getReason();
            }
        }
        if (nacked + returned > 0) {
            throw new AmqpException(nacked + " nacked and " + returned + " returned of " + pending.size()
                    + " event(s) (" + reason + ")");
        }
    }

    private static CorrelationData.Confirm await(CorrelationData correlation, long deadline) {
        try {
            long remaining = Math.max(0, deadline - System.nanoTime());
            return correlation.getFuture().get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for publisher confirms", e);
        } catch (TimeoutException e) {
            throw new AmqpException("RabbitMQ did not confirm event " + correlation.getId() + " in time");
        } catch (ExecutionException e) {
            throw new AmqpException("Publisher confirm failed for event " + correlation.getId(), e.getCause());
        }
    }

    private void declareOnce() {
        if (declared) {
            return;
        }
        AmqpAdmin admin = amqpAdmin.getIfAvailable();
        if (admin == null) {
            declared = true;
            return;
        }
        try {
            admin.initialize();
            declared = true;
        } catch (RuntimeException e) {
            log.debug("[ACTIVITY] Could not declare the activity exchange yet: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        if (ownFactory != null) {
            ownFactory.destroy();
        }
    }
}
