package com.itways.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** The shared DLQ gauge (ARC-11): what it reports, and its own thread's life. */
@ExtendWith(OutputCaptureExtension.class)
class DeadLetterQueueGaugeTest {

    private static final String QUEUE = "test.dlq";
    private static final String METRIC = "test.dlq.messages";

    private final AmqpAdmin admin = mock(AmqpAdmin.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    /** A gauge whose own thread never fires during the test (a day of delay). */
    private DeadLetterQueueGauge idle() {
        return new DeadLetterQueueGauge(admin, METRIC, QUEUE, "Messages parked in " + QUEUE, Duration.ofDays(1),
                Duration.ofDays(1));
    }

    private Gauge registered() {
        Gauge gauge = registry.find(METRIC).tag("queue", QUEUE).gauge();
        assertThat(gauge).isNotNull();
        return gauge;
    }

    @Test
    void theGaugeIsUnknownUntilTheBrokerAnswersAndCarriesTheQueueTag() {
        try (DeadLetterQueueGauge gauge = idle()) {
            gauge.bindTo(registry);

            assertThat(registered().value()).isEqualTo(-1);
            assertThat(registered().getId().getDescription()).isEqualTo("Messages parked in " + QUEUE);
            assertThat(gauge.depth()).isEqualTo(DeadLetterQueueGauge.UNKNOWN);
            assertThat(gauge.metricName()).isEqualTo(METRIC);
            assertThat(gauge.queueName()).isEqualTo(QUEUE);
        }
    }

    @Test
    void refreshReadsTheDepth() {
        when(admin.getQueueInfo(QUEUE)).thenReturn(new QueueInformation(QUEUE, 7, 1));
        try (DeadLetterQueueGauge gauge = idle()) {
            gauge.bindTo(registry);

            gauge.refresh();

            assertThat(gauge.depth()).isEqualTo(7);
            assertThat(registered().value()).isEqualTo(7);
        }
    }

    @Test
    void aMissingQueueIsUnknownAgain() {
        when(admin.getQueueInfo(QUEUE)).thenReturn(new QueueInformation(QUEUE, 7, 1)).thenReturn(null);
        try (DeadLetterQueueGauge gauge = idle()) {
            gauge.refresh();
            assertThat(gauge.depth()).isEqualTo(7);

            gauge.refresh();
            assertThat(gauge.depth()).isEqualTo(-1);
        }
    }

    @Test
    void aBrokerFailureIsUnknownAndNeverLogsTheMessage(CapturedOutput output) {
        when(admin.getQueueInfo(QUEUE)).thenReturn(new QueueInformation(QUEUE, 3, 1))
                .thenThrow(new AmqpConnectException(new RuntimeException("amqp://user:secret@broker:5672")));
        try (DeadLetterQueueGauge gauge = idle()) {
            gauge.refresh();
            gauge.refresh();

            assertThat(gauge.depth()).isEqualTo(-1);
            assertThat(output.getAll()).doesNotContain("secret@broker");
        }
    }

    @Test
    void theReadsRunOnTheGaugesOwnDaemonThreadAndStopOnClose() throws Exception {
        when(admin.getQueueInfo(QUEUE)).thenReturn(new QueueInformation(QUEUE, 2, 0));
        DeadLetterQueueGauge gauge = new DeadLetterQueueGauge(admin, METRIC, QUEUE, null, Duration.ZERO,
                Duration.ofMillis(20));

        gauge.bindTo(registry);
        gauge.bindTo(registry); // idempotent: one thread

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(admin, atLeast(3)).getQueueInfo(QUEUE));
        assertThat(registered().value()).isEqualTo(2);
        assertThat(Thread.getAllStackTraces().keySet())
                .filteredOn(thread -> thread.getName().equals("dlq-gauge-" + QUEUE) && thread.isAlive())
                .isNotEmpty()
                .allMatch(Thread::isDaemon);

        gauge.destroy();
        Thread.sleep(100);
        long calls = mockingDetails(admin).getInvocations().size();
        Thread.sleep(150);
        assertThat(mockingDetails(admin).getInvocations().size()).isEqualTo(calls);
        // The gauge keeps its last value.
        assertThat(gauge.depth()).isEqualTo(2);
    }

    @Test
    void theDefaultsAreTenAndThirtySeconds() {
        assertThat(DeadLetterQueueGauge.DEFAULT_INITIAL_DELAY).isEqualTo(Duration.ofSeconds(10));
        assertThat(DeadLetterQueueGauge.DEFAULT_REFRESH_INTERVAL).isEqualTo(Duration.ofSeconds(30));
        try (DeadLetterQueueGauge gauge = new DeadLetterQueueGauge(admin, METRIC, QUEUE, null)) {
            gauge.bindTo(registry);
            assertThat(registered().getId().getDescription()).isEqualTo("Messages in " + QUEUE
                    + " (-1: broker unreachable)");
        }
        verify(admin, org.mockito.Mockito.never()).getQueueInfo(anyString());
    }

    @Test
    void badArgumentsAreRefused() {
        assertThatThrownBy(() -> new DeadLetterQueueGauge(null, METRIC, QUEUE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeadLetterQueueGauge(admin, " ", QUEUE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeadLetterQueueGauge(admin, METRIC, "", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeadLetterQueueGauge(admin, METRIC, QUEUE, null, Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeadLetterQueueGauge(admin, METRIC, QUEUE, null, Duration.ofSeconds(-1),
                Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
