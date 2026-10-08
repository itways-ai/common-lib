package com.itways.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.DisposableBean;

/**
 * A gauge of how many messages are parked in a dead-letter queue (ARC-11; the
 * one version of account-service's and notification-service's
 * {@code DeadLetterQueueGauge}). Each one is a message the service could not
 * handle; without the gauge only the RabbitMQ console shows them.
 *
 * <p>
 * The depth is read from the broker every {@code refreshInterval} (default
 * {@link #DEFAULT_REFRESH_INTERVAL}, after {@link #DEFAULT_INITIAL_DELAY})
 * rather than on each scrape, so a slow or absent broker never slows the
 * metrics endpoint. While the broker cannot be asked, or the queue does not
 * exist, the gauge reports -1; a failure is logged at DEBUG with the
 * exception's class name only. The reads run on the gauge's own daemon thread,
 * started when the registry binds it and stopped when the bean is destroyed,
 * so the service needs no {@code @EnableScheduling}.
 *
 * <p>
 * The gauge carries the tag {@code queue=<queueName>} besides whatever the
 * registry adds ({@code application}). A service declares the bean and names
 * the metric:
 *
 * <pre>
 * &#64;Bean
 * DeadLetterQueueGauge notificationDlqGauge(AmqpAdmin admin) {
 *     return new DeadLetterQueueGauge(admin, "notification.dlq.messages", "notification.dlq",
 *             "Notifications in notification.dlq (-1: broker unreachable)");
 * }
 * </pre>
 *
 * Needs micrometer-core (Actuator) on the service's classpath: the class is
 * loaded only by a service that declares it, so common-messaging keeps
 * micrometer optional.
 */
@Slf4j
public class DeadLetterQueueGauge implements MeterBinder, DisposableBean, AutoCloseable {

    public static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(10);
    public static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofSeconds(30);
    /** The gauge's value while the broker has not answered (yet). */
    public static final long UNKNOWN = -1;

    private final AmqpAdmin amqpAdmin;
    private final String metricName;
    private final String queueName;
    private final String description;
    private final Duration initialDelay;
    private final Duration refreshInterval;
    private final AtomicLong depth = new AtomicLong(UNKNOWN);
    private final AtomicBoolean started = new AtomicBoolean();

    private volatile ScheduledExecutorService scheduler;

    public DeadLetterQueueGauge(AmqpAdmin amqpAdmin, String metricName, String queueName, String description) {
        this(amqpAdmin, metricName, queueName, description, DEFAULT_INITIAL_DELAY, DEFAULT_REFRESH_INTERVAL);
    }

    public DeadLetterQueueGauge(AmqpAdmin amqpAdmin, String metricName, String queueName, String description,
            Duration initialDelay, Duration refreshInterval) {
        if (amqpAdmin == null || metricName == null || metricName.isBlank() || queueName == null
                || queueName.isBlank()) {
            throw new IllegalArgumentException("DeadLetterQueueGauge needs an AmqpAdmin, a metric name and a queue name");
        }
        if (initialDelay == null || initialDelay.isNegative() || refreshInterval == null
                || refreshInterval.isZero() || refreshInterval.isNegative()) {
            throw new IllegalArgumentException("DeadLetterQueueGauge needs a non-negative initial delay and a "
                    + "positive refresh interval; got " + initialDelay + " / " + refreshInterval);
        }
        this.amqpAdmin = amqpAdmin;
        this.metricName = metricName;
        this.queueName = queueName;
        this.description = description != null ? description
                : "Messages in " + queueName + " (-1: broker unreachable)";
        this.initialDelay = initialDelay;
        this.refreshInterval = refreshInterval;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(metricName, depth, AtomicLong::get)
                .description(description)
                .tag("queue", queueName)
                .register(registry);
        start();
    }

    /** Reads the depth from the broker once; -1 when it cannot be read or the queue does not exist. */
    public void refresh() {
        try {
            QueueInformation info = amqpAdmin.getQueueInfo(queueName);
            depth.set(info != null ? info.getMessageCount() : UNKNOWN);
        } catch (RuntimeException e) {
            // The class name only: the message may name the broker's address and credentials.
            log.debug("Could not read the depth of {}: {}", queueName, e.getClass().getSimpleName());
            depth.set(UNKNOWN);
        }
    }

    /** The last depth read, or -1 while unknown. */
    public long depth() {
        return depth.get();
    }

    public String metricName() {
        return metricName;
    }

    public String queueName() {
        return queueName;
    }

    /** Starts the periodic reads (once); {@link #bindTo} does this. */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "dlq-gauge-" + queueName);
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::refresh, initialDelay.toMillis(), refreshInterval.toMillis(),
                TimeUnit.MILLISECONDS);
        scheduler = executor;
    }

    /** Stops the periodic reads; the gauge keeps its last value. */
    @Override
    public void close() {
        ScheduledExecutorService executor = scheduler;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public void destroy() {
        close();
    }
}
