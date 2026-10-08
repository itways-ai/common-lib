package com.itways.it.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itways.activity.dto.AccountActivityEvent;
import com.itways.activity.dto.ActivityCategory;
import com.itways.activity.dto.ActivitySeverity;
import com.itways.activity.outbox.ActivityOutbox;
import com.itways.activity.outbox.ActivityOutboxConfig;
import com.itways.activity.outbox.ActivityOutboxProperties;
import com.itways.activity.outbox.ActivityOutboxRelay;
import com.itways.activity.outbox.ActivityOutboxStore;
import com.itways.activity.outbox.RabbitConfirmedSender;
import com.itways.annotation.EnableActivity;
import com.itways.common.correlation.RequestIds;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The activity outbox (PLT-07) against a real Postgres and RabbitMQ: a rollback
 * writes and sends nothing, a commit sends exactly once, a broker outage keeps the
 * rows and sends them after recovery, and two relays never send one row twice.
 *
 * <p>
 * Outside {@code com.itways.activity}, where it once had to be: {@code @EnableActivity}
 * used to scan that package and would have picked up this test's configuration in
 * every other context.
 */
@SpringBootTest(classes = ActivityOutboxIT.App.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "itways.activity.outbox.enabled=true",
        "itways.activity.outbox.table=" + ActivityOutboxIT.TABLE,
        "itways.activity.outbox.poll-interval=200ms",
        "itways.activity.outbox.initial-backoff=200ms",
        "itways.activity.outbox.max-backoff=1s",
        "itways.activity.outbox.confirm-timeout=3s",
        "itways.activity.outbox.stats-interval=500ms",
        "itways.activity.outbox.batch-size=10" })
@Testcontainers(disabledWithoutDocker = true)
class ActivityOutboxIT {

    static final String TABLE = "test_activity_outbox";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-alpine").asCompatibleSubstituteFor("rabbitmq"));

    @SpringBootConfiguration
    @ImportAutoConfiguration({ DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class, TransactionAutoConfiguration.class, RabbitAutoConfiguration.class })
    @EnableActivity
    static class App {
    }

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    /** The table as the services' migrations create it. */
    @BeforeAll
    static void schema() throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); Statement sql = connection.createStatement()) {
            sql.execute("""
                    CREATE TABLE IF NOT EXISTS test_activity_outbox (
                        id              uuid         NOT NULL,
                        payload         jsonb        NOT NULL,
                        created_at      timestamptz  NOT NULL DEFAULT now(),
                        attempts        integer      NOT NULL DEFAULT 0,
                        next_attempt_at timestamptz  NOT NULL DEFAULT now(),
                        sent_at         timestamptz,
                        last_error      varchar(500),
                        CONSTRAINT pk_test_activity_outbox PRIMARY KEY (id)
                    );
                    CREATE INDEX IF NOT EXISTS idx_test_activity_outbox_due
                        ON test_activity_outbox (next_attempt_at) WHERE sent_at IS NULL;
                    CREATE INDEX IF NOT EXISTS idx_test_activity_outbox_sent
                        ON test_activity_outbox (sent_at) WHERE sent_at IS NOT NULL;
                    CREATE TABLE IF NOT EXISTS test_business (id uuid PRIMARY KEY);
                    """);
        }
    }

    @Autowired
    ActivityOutbox outbox;
    @Autowired
    ActivityOutboxRelay relay;
    @Autowired
    ActivityOutboxStore store;
    @Autowired
    RabbitConfirmedSender sender;
    @Autowired
    ActivityOutboxProperties properties;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RabbitTemplate rabbit;
    @Autowired
    AmqpAdmin admin;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM " + TABLE);
        receiveAll(Duration.ofMillis(300));
    }

    @Test
    void aRolledBackChangeWritesAndSendsNothing() {
        UUID business = UUID.randomUUID();
        AccountActivityEvent event = event();

        transaction().executeWithoutResult(status -> {
            jdbc.update("INSERT INTO test_business (id) VALUES (?)", business);
            outbox.record(event);
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("SELECT count(*) FROM test_business WHERE id = ?", Integer.class, business))
                .isZero();
        assertThat(rowCount(event.getEventId())).isZero();
        assertThat(relay.relayPending()).isZero();
        assertThat(receiveAll(Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    void aCommittedChangeSendsItsEventExactlyOnce() {
        UUID business = UUID.randomUUID();
        AccountActivityEvent event = event();

        transaction().executeWithoutResult(status -> {
            jdbc.update("INSERT INTO test_business (id) VALUES (?)", business);
            outbox.record(event);
        });

        // Sent by the relay the commit woke, well before the next poll would matter.
        await().atMost(Duration.ofSeconds(10)).until(() -> sentAt(event.getEventId()) != null);
        List<Message> received = receiveAll(Duration.ofSeconds(1));
        assertThat(received).hasSize(1);
        Message message = received.get(0);
        assertThat(eventId(message)).isEqualTo(event.getEventId());
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.getEventId().toString());
        // The wire format of the direct publisher: Jackson JSON with the DTO's type id.
        assertThat(message.getMessageProperties().getHeaders())
                .containsEntry("__TypeId__", AccountActivityEvent.class.getName());
        assertThat(read(message).get("action")).isEqualTo("LOGIN_SUCCESS");

        assertThat(relay.relayPending()).isZero();
        assertThat(receiveAll(Duration.ofMillis(800))).isEmpty();
    }

    /** ARC-25: the relay sends later, on its own thread; the id recorded with the event goes with it. */
    @Test
    void theRequestIdRecordedWithAnEventTravelsAsTheMessageHeader() {
        AccountActivityEvent withId = event();
        AccountActivityEvent withoutId = event();
        MDC.put(RequestIds.MDC_KEY, "req-it-1");
        try {
            transaction().executeWithoutResult(status -> outbox.record(withId));
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
        transaction().executeWithoutResult(status -> outbox.record(withoutId));

        await().atMost(Duration.ofSeconds(10))
                .until(() -> sentAt(withId.getEventId()) != null && sentAt(withoutId.getEventId()) != null);
        Map<UUID, Message> received = receiveAll(Duration.ofSeconds(1)).stream()
                .collect(Collectors.toMap(this::eventId, Function.identity()));

        Message carried = received.get(withId.getEventId());
        assertThat((String) carried.getMessageProperties().getHeader(RequestIds.AMQP_HEADER)).isEqualTo("req-it-1");
        assertThat(read(carried).get("requestId")).isEqualTo("req-it-1");
        Message plain = received.get(withoutId.getEventId());
        assertThat(plain.getMessageProperties().getHeaders()).doesNotContainKey(RequestIds.AMQP_HEADER);
        assertThat(read(plain)).doesNotContainKey("requestId");
    }

    @Test
    void aBrokerOutageKeepsTheRowsAndTheyAreSentAfterRecovery() throws Exception {
        List<UUID> ids = new ArrayList<>();
        exec("rabbitmqctl", "stop_app");
        try {
            transaction().executeWithoutResult(status -> {
                for (int i = 0; i < 3; i++) {
                    AccountActivityEvent event = event();
                    ids.add(event.getEventId());
                    outbox.record(event);
                }
            });
            await().atMost(Duration.ofSeconds(30)).until(() -> ids.stream().allMatch(id -> attempts(id) >= 1));
            assertThat(ids).allSatisfy(id -> assertThat(sentAt(id)).isNull());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + TABLE + " WHERE last_error IS NOT NULL",
                    Integer.class)).isGreaterThanOrEqualTo(3);
            relay.refreshStats();
            assertThat(relay.stats().pending()).isGreaterThanOrEqualTo(3);
        } finally {
            exec("rabbitmqctl", "start_app");
        }

        await().atMost(Duration.ofSeconds(60)).until(() -> ids.stream().allMatch(id -> sentAt(id) != null));
        Map<UUID, Long> received = counts(receiveAll(Duration.ofSeconds(1)));
        assertThat(received).containsOnlyKeys(ids);
        assertThat(received.values()).containsOnly(1L);
    }

    @Test
    void twoRelaysNeverSendOneRowTwice() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            AccountActivityEvent event = event();
            ids.add(event.getEventId());
            // Straight into the table: no commit nudge, the relays below find them.
            store.insert(event.getEventId(), ActivityOutboxConfig.outboxObjectMapper().writeValueAsString(event));
        }
        ActivityOutboxProperties small = new ActivityOutboxProperties();
        small.setTable(TABLE);
        small.setBatchSize(5);
        ActivityOutboxRelay first = new ActivityOutboxRelay(store, sender, requiresNew(), ActivityOutboxConfig
                .outboxObjectMapper(), small);
        ActivityOutboxRelay second = new ActivityOutboxRelay(store, sender, requiresNew(), ActivityOutboxConfig
                .outboxObjectMapper(), small);

        ExecutorService threads = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> sent = new ArrayList<>();
            for (ActivityOutboxRelay relayUnderTest : List.of(first, second)) {
                sent.add(threads.submit(() -> {
                    go.await();
                    int total = 0;
                    while (pending() > 0) {
                        total += relayUnderTest.relayPending();
                    }
                    return total;
                }));
            }
            go.countDown();
            for (Future<Integer> future : sent) {
                future.get();
            }
        } finally {
            threads.shutdownNow();
        }

        Map<UUID, Long> received = counts(receiveAll(Duration.ofSeconds(2)));
        assertThat(received).containsOnlyKeys(ids);
        assertThat(received.values()).containsOnly(1L);
    }

    @Test
    void sentRowsAreDeletedAfterTheRetention() {
        AccountActivityEvent old = event();
        AccountActivityEvent recent = event();
        transaction().executeWithoutResult(status -> {
            outbox.record(old);
            outbox.record(recent);
        });
        await().atMost(Duration.ofSeconds(10))
                .until(() -> sentAt(old.getEventId()) != null && sentAt(recent.getEventId()) != null);
        jdbc.update("UPDATE " + TABLE + " SET sent_at = now() - interval '8 days' WHERE id = ?", old.getEventId());

        assertThat(properties.getRetention()).isEqualTo(Duration.ofDays(7));
        assertThat(relay.cleanup()).isEqualTo(1);
        assertThat(rowCount(old.getEventId())).isZero();
        assertThat(rowCount(recent.getEventId())).isOne();
        receiveAll(Duration.ofMillis(300));
    }

    // ─────────────── publisher confirms and returns (PLT-30) ───────────────

    /** common-lib's defaults give the service's connection confirms and returns: no extra connection. */
    @Test
    void theRelayPublishesOnTheServiceConnection() {
        CachingConnectionFactory factory = (CachingConnectionFactory) rabbit.getConnectionFactory();
        assertThat(factory.isPublisherConfirms()).isTrue();
        assertThat(factory.isSimplePublisherConfirms()).isFalse();
        assertThat(factory.isPublisherReturns()).isTrue();
        assertThat(sender.sharesTheServiceConnection()).isTrue();
    }

    /** Acked but routed nowhere (the queue's binding is gone): not sent; the next attempt re-declares it. */
    @Test
    void anUnroutableBatchIsNotCountedAsSent() {
        sender.send(List.of(event())); // declared
        receiveAll(Duration.ofMillis(300));
        admin.removeBinding(activityBinding());

        AccountActivityEvent lost = event();
        assertThatThrownBy(() -> sender.send(List.of(lost))).isInstanceOf(AmqpException.class)
                .hasMessageContaining("1 returned");
        assertThat(receiveAll(Duration.ofMillis(500))).isEmpty();

        AccountActivityEvent next = event();
        sender.send(List.of(next));
        assertThat(counts(receiveAll(Duration.ofSeconds(1)))).containsOnlyKeys(next.getEventId());
    }

    /** The relay keeps a returned row and sends it once the binding is back. */
    @Test
    void aReturnedEventStaysInTheOutboxUntilItIsDelivered() {
        sender.send(List.of(event()));
        receiveAll(Duration.ofMillis(300));
        admin.removeBinding(activityBinding());

        AccountActivityEvent event = event();
        transaction().executeWithoutResult(status -> outbox.record(event));

        await().atMost(Duration.ofSeconds(30)).until(() -> sentAt(event.getEventId()) != null);
        assertThat(attempts(event.getEventId())).isGreaterThanOrEqualTo(1);
        assertThat(counts(receiveAll(Duration.ofSeconds(1)))).containsOnlyKeys(event.getEventId());
    }

    /** A publish the broker refuses (exchange deleted: the channel closes, pending confirms are nacked). */
    @Test
    void aRefusedPublishFailsTheBatchAndTheNextOneRedeclares() {
        sender.send(List.of(event()));
        receiveAll(Duration.ofMillis(300));
        admin.deleteExchange(AccountActivityEvent.EXCHANGE_NAME);

        assertThatThrownBy(() -> sender.send(List.of(event(), event()))).isInstanceOf(AmqpException.class);

        AccountActivityEvent next = event();
        sender.send(List.of(next));
        assertThat(counts(receiveAll(Duration.ofSeconds(1)))).containsOnlyKeys(next.getEventId());
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private static AccountActivityEvent event() {
        return AccountActivityEvent.builder()
                .eventId(UUID.randomUUID())
                .accountId("acc-it")
                .category(ActivityCategory.SECURITY)
                .action("LOGIN_SUCCESS")
                .title("Signed in")
                .metadata(Map.of("n", 1))
                .occurredAt(Instant.now())
                .severity(ActivitySeverity.INFO)
                .build();
    }

    private static Binding activityBinding() {
        return new Binding(AccountActivityEvent.QUEUE_NAME, Binding.DestinationType.QUEUE,
                AccountActivityEvent.EXCHANGE_NAME, AccountActivityEvent.ROUTING_KEY, null);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void exec(String... command) throws Exception {
        var result = RABBIT.execInContainer(command);
        assertThat(result.getExitCode()).as(String.join(" ", command) + ": " + result.getStderr()).isZero();
    }

    private long pending() {
        return jdbc.queryForObject("SELECT count(*) FROM " + TABLE + " WHERE sent_at IS NULL", Long.class);
    }

    private int rowCount(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + TABLE + " WHERE id = ?", Integer.class, id);
    }

    private Instant sentAt(UUID id) {
        List<java.sql.Timestamp> rows = jdbc.queryForList("SELECT sent_at FROM " + TABLE + " WHERE id = ?",
                java.sql.Timestamp.class, id);
        return rows.isEmpty() || rows.get(0) == null ? null : rows.get(0).toInstant();
    }

    private int attempts(UUID id) {
        List<Integer> rows = jdbc.queryForList("SELECT attempts FROM " + TABLE + " WHERE id = ?", Integer.class, id);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private List<Message> receiveAll(Duration quiet) {
        List<Message> messages = new ArrayList<>();
        Message message;
        while ((message = rabbit.receive(AccountActivityEvent.QUEUE_NAME, quiet.toMillis())) != null) {
            messages.add(message);
        }
        return messages;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(Message message) {
        try {
            return json.readValue(message.getBody(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private UUID eventId(Message message) {
        return UUID.fromString(String.valueOf(read(message).get("eventId")));
    }

    private Map<UUID, Long> counts(List<Message> messages) {
        return messages.stream().map(this::eventId)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
