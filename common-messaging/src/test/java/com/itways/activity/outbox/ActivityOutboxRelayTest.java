package com.itways.activity.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpIOException;
import org.springframework.boot.actuate.health.Status;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itways.activity.dto.AccountActivityEvent;

class ActivityOutboxRelayTest {

    private final ActivityOutboxStore store = mock(ActivityOutboxStore.class);
    private final ActivityBatchSender sender = mock(ActivityBatchSender.class);
    private final ObjectMapper json = ActivityOutboxConfig.outboxObjectMapper();
    private final ActivityOutboxProperties properties = new ActivityOutboxProperties();
    private final ActivityOutboxRelay relay = new ActivityOutboxRelay(store, sender,
            new TransactionTemplate(new StubTransactionManager()), json, properties);

    {
        properties.setTable("test_activity_outbox");
        properties.setBatchSize(2);
        when(store.table()).thenReturn("test_activity_outbox");
    }

    @Test
    void confirmedRowsAreMarkedSent() throws Exception {
        ActivityOutboxStore.Row a = row(0);
        when(store.claimDue(2)).thenReturn(List.of(a)).thenReturn(List.of());

        assertThat(relay.relayOnce()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AccountActivityEvent>> sent = ArgumentCaptor.forClass(List.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue()).extracting(AccountActivityEvent::getEventId).containsExactly(a.id());
        verify(store).markSent(List.of(a.id()));
        verify(store, never()).markFailed(any(), any(), any());
    }

    @Test
    void anUnconfirmedBatchIsRetriedLaterWithBackoffPerRow() throws Exception {
        ActivityOutboxStore.Row first = row(0);
        ActivityOutboxStore.Row third = row(2);
        when(store.claimDue(2)).thenReturn(List.of(first, third));
        doThrow(new AmqpIOException(new java.io.IOException("connection refused"))).when(sender).send(anyList());

        assertThat(relay.relayOnce()).isZero();

        verify(store).markFailed(eq(first.id()), eq(Duration.ofSeconds(1)), startsWith("AmqpIOException"));
        verify(store).markFailed(eq(third.id()), eq(Duration.ofSeconds(4)), startsWith("AmqpIOException"));
        verify(store, never()).markSent(anyList());
    }

    @Test
    void theBackoffDoublesUpToTheMaximum() {
        properties.setInitialBackoff(Duration.ofSeconds(1));
        properties.setMaxBackoff(Duration.ofMinutes(5));

        assertThat(relay.backoff(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(relay.backoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(relay.backoff(8)).isEqualTo(Duration.ofSeconds(256));
        assertThat(relay.backoff(9)).isEqualTo(Duration.ofMinutes(5));
        assertThat(relay.backoff(10_000)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void anUnreadableRowIsSetAsideAndTheRestIsSent() throws Exception {
        ActivityOutboxStore.Row good = row(0);
        ActivityOutboxStore.Row bad = new ActivityOutboxStore.Row(UUID.randomUUID(), "{not json", 0);
        when(store.claimDue(2)).thenReturn(List.of(bad, good));

        assertThat(relay.relayOnce()).isEqualTo(1);

        verify(store).markFailed(eq(bad.id()), eq(properties.getMaxBackoff()), startsWith("Unreadable payload"));
        verify(store).markSent(List.of(good.id()));
    }

    @Test
    void relayPendingDrainsFullBatchesAndStopsAtAPartialOne() throws Exception {
        when(store.claimDue(2)).thenReturn(List.of(row(0), row(0)), List.of(row(0), row(0)), List.of(row(0)));

        assertThat(relay.relayPending()).isEqualTo(5);

        verify(store, times(3)).claimDue(anyInt());
        verify(sender, times(3)).send(anyList());
    }

    @Test
    void relayPendingStopsAtAFailedBatch() throws Exception {
        when(store.claimDue(2)).thenReturn(List.of(row(0), row(0)));
        doThrow(new AmqpIOException(new java.io.IOException("down"))).when(sender).send(anyList());

        assertThat(relay.relayPending()).isZero();

        verify(store, times(1)).claimDue(anyInt());
    }

    @Test
    void nothingDueSendsNothing() {
        when(store.claimDue(2)).thenReturn(List.of());

        assertThat(relay.relayOnce()).isZero();

        verify(sender, never()).send(anyList());
    }

    @Test
    void cleanupDeletesSentRowsPastTheRetention() {
        properties.setRetention(Duration.ofDays(7));
        when(store.deleteSentBefore(Duration.ofDays(7))).thenReturn(4);

        assertThat(relay.cleanup()).isEqualTo(4);
    }

    @Test
    void healthIsUnknownUntilReadThenUpThenWarningWhenTheOldestRowWaitsTooLong() {
        properties.setLagWarning(Duration.ofMinutes(5));
        ActivityOutboxHealthIndicator health = new ActivityOutboxHealthIndicator(relay);
        assertThat(health.health().getStatus()).isEqualTo(Status.UNKNOWN);

        when(store.stats()).thenReturn(new ActivityOutboxStore.Stats(3, 12));
        relay.refreshStats();
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
        assertThat(health.health().getDetails()).containsEntry("pending", 3L)
                .containsEntry("oldestPendingAgeSeconds", 12L);

        when(store.stats()).thenReturn(new ActivityOutboxStore.Stats(40, 301));
        relay.refreshStats();
        assertThat(health.health().getStatus()).isEqualTo(ActivityOutboxHealthIndicator.WARNING);

        when(store.stats()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("down"));
        relay.refreshStats();
        assertThat(health.health().getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void theGaugesReadTheLastStatsAndAreMinusOneWhileUnknown() {
        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        new ActivityOutboxMetrics(relay).bindTo(registry);
        assertThat(registry.get("activity.outbox.pending").tag("table", "test_activity_outbox").gauge().value())
                .isEqualTo(-1);

        when(store.stats()).thenReturn(new ActivityOutboxStore.Stats(7, 90));
        relay.refreshStats();

        assertThat(registry.get("activity.outbox.pending").gauge().value()).isEqualTo(7);
        assertThat(registry.get("activity.outbox.oldest.pending.age").gauge().value()).isEqualTo(90);
    }

    @Test
    void aNudgeBeforeStartIsIgnoredAndOneAfterStartRelays() throws Exception {
        properties.setPollInterval(Duration.ofHours(1));
        properties.setCleanupInterval(Duration.ofHours(1));
        properties.setStatsInterval(Duration.ofHours(1));
        when(store.claimDue(2)).thenReturn(List.of(row(0))).thenReturn(List.of());
        when(store.stats()).thenReturn(new ActivityOutboxStore.Stats(0, 0));

        relay.nudge();
        verify(sender, never()).send(anyList());

        relay.start();
        try {
            relay.nudge();
            verify(sender, org.mockito.Mockito.timeout(2000)).send(anyList());
        } finally {
            relay.stop();
        }
        assertThat(relay.isRunning()).isFalse();
    }

    @Test
    void theTableNameMustBeAPlainIdentifier() {
        ActivityOutboxProperties p = new ActivityOutboxProperties();
        for (String bad : new String[] { null, "", "Auth_Outbox", "x; drop table users", "a-b", "1abc" }) {
            p.setTable(bad);
            org.assertj.core.api.Assertions.assertThatThrownBy(p::requireTable)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("itways.activity.outbox.table");
        }
        p.setTable("auth_activity_outbox");
        assertThat(p.requireTable()).isEqualTo("auth_activity_outbox");
    }

    private ActivityOutboxStore.Row row(int attempts) throws Exception {
        AccountActivityEvent event = ActivityOutboxTest.event();
        event.setEventId(UUID.randomUUID());
        return new ActivityOutboxStore.Row(event.getEventId(), json.writeValueAsString(event), attempts);
    }
}
