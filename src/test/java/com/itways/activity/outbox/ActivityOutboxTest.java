package com.itways.activity.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itways.activity.dto.AccountActivityEvent;
import com.itways.activity.dto.ActivityCategory;
import com.itways.activity.dto.ActivitySeverity;

class ActivityOutboxTest {

    private final StubTransactionManager transactions = new StubTransactionManager();
    private final ActivityOutboxStore store = mock(ActivityOutboxStore.class);
    private final ActivityOutboxRelay relay = mock(ActivityOutboxRelay.class);
    private final ObjectMapper json = ActivityOutboxConfig.outboxObjectMapper();
    private final ActivityOutbox outbox = new ActivityOutbox(store, json, requiresNew(), relay);
    /** Whether a transaction was active on the thread at each insert. */
    private final List<Boolean> insertedInTransaction = new ArrayList<>();

    {
        doAnswer(invocation -> insertedInTransaction.add(TransactionSynchronizationManager.isActualTransactionActive()))
                .when(store).insert(any(), anyString());
    }

    @Test
    void insideATransactionTheRowJoinsItAndTheRelayIsWokenOnlyAfterTheCommit() {
        AccountActivityEvent event = event();
        caller().executeWithoutResult(status -> {
            outbox.record(event);
            verify(store).insert(eq(event.getEventId()), anyString());
            verify(relay, never()).nudge();
        });

        assertThat(transactions.begun).hasValue(1); // the caller's only
        verify(relay).nudge();
    }

    @Test
    void aRolledBackCallerWakesNothing() {
        caller().executeWithoutResult(status -> {
            outbox.record(event());
            status.setRollbackOnly();
        });

        verify(relay, never()).nudge();
    }

    @Test
    void withoutATransactionTheRowIsWrittenInItsOwn() {
        outbox.record(event());

        assertThat(transactions.begun).hasValue(1);
        assertThat(insertedInTransaction).containsExactly(true);
        verify(relay).nudge();
    }

    @Test
    void aReadOnlyCallerGetsItsOwnWritableTransaction() {
        TransactionTemplate readOnly = caller();
        readOnly.setReadOnly(true);

        readOnly.executeWithoutResult(status -> outbox.record(event()));

        assertThat(transactions.begun).hasValue(2);
    }

    @Test
    void anIndependentEventIsWrittenInItsOwnTransactionEvenWhenTheCallerRollsBack() {
        caller().executeWithoutResult(status -> {
            outbox.recordIndependently(event());
            verify(relay).nudge(); // committed already
            status.setRollbackOnly();
        });

        assertThat(transactions.begun).hasValue(2);
    }

    @Test
    void aJoinedWriteThatFailsFailsTheCaller() {
        doThrow(new DataAccessResourceFailureException("down")).when(store).insert(any(), anyString());

        assertThatThrownBy(() -> caller().executeWithoutResult(status -> outbox.record(event())))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void anIndependentWriteThatFailsIsOnlyLogged() {
        doThrow(new DataAccessResourceFailureException("down")).when(store).insert(any(), anyString());

        outbox.recordIndependently(event());

        verify(relay, never()).nudge();
    }

    @Test
    void anEventWithoutAnAccountIsSkipped() {
        outbox.record(AccountActivityEvent.builder().action("X").build());
        outbox.recordIndependently(AccountActivityEvent.builder().accountId(" ").build());
        outbox.record(null);

        verifyNoInteractions(store, relay);
    }

    @Test
    void theEventGetsAnIdAndATimeWhenItHasNone() throws Exception {
        AccountActivityEvent event = AccountActivityEvent.builder().accountId("acc-1").action("X").build();
        String[] payload = new String[1];
        doAnswer(invocation -> payload[0] = invocation.getArgument(1)).when(store).insert(any(), anyString());

        outbox.record(event);

        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getOccurredAt()).isNotNull();
        verify(store).insert(eq(event.getEventId()), anyString());
        assertThat(json.readValue(payload[0], AccountActivityEvent.class).getEventId()).isEqualTo(event.getEventId());
    }

    @Test
    void theStoredJsonReadsBackAsTheSameEvent() throws Exception {
        AccountActivityEvent event = event();
        String[] payload = new String[1];
        doAnswer(invocation -> payload[0] = invocation.getArgument(1)).when(store).insert(any(), anyString());

        outbox.record(event);

        assertThat(payload[0]).contains("\"occurredAt\":\"2026-09-27T10:15:30.123456Z\"");
        assertThat(json.readValue(payload[0], AccountActivityEvent.class)).isEqualTo(event);
    }

    private TransactionTemplate caller() {
        return new TransactionTemplate(transactions);
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    static AccountActivityEvent event() {
        return AccountActivityEvent.builder()
                .eventId(UUID.randomUUID())
                .accountId("acc-1")
                .category(ActivityCategory.SECURITY)
                .action("LOGIN_SUCCESS")
                .title("Signed in")
                .metadata(Map.of("name", "x", "count", 3))
                .occurredAt(Instant.parse("2026-09-27T10:15:30.123456Z"))
                .ipAddress("10.0.0.1")
                .severity(ActivitySeverity.INFO)
                .build();
    }
}
