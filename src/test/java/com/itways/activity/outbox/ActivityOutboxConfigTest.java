package com.itways.activity.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import com.itways.activity.publisher.ActivityEventPublisher;
import com.itways.annotation.EnableActivity;

/** Without the property a service keeps today's behaviour: the publisher, and nothing of the outbox. */
class ActivityOutboxConfigTest {

    /** Not a @Configuration: @EnableActivity's component scan of com.itways.activity would pick it up everywhere. */
    @EnableActivity
    static class Service {

        @Bean
        RabbitTemplate rabbitTemplate() {
            return mock(RabbitTemplate.class);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Service.class);

    @Test
    void offByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ActivityEventPublisher.class);
            assertThat(context).doesNotHaveBean(ActivityOutbox.class);
            assertThat(context).doesNotHaveBean(ActivityOutboxRelay.class);
            assertThat(context).doesNotHaveBean(ActivityOutboxMetrics.class);
            assertThat(context).doesNotHaveBean(ActivityOutboxHealthIndicator.class);
        });
    }

    @Test
    void offWhenFalse() {
        runner.withPropertyValues("itways.activity.outbox.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ActivityOutbox.class);
            assertThat(context).doesNotHaveBean(ActivityOutboxHealthIndicator.class);
        });
    }

    @Test
    void onWithoutATableNameTheServiceDoesNotStart() {
        runner.withPropertyValues("itways.activity.outbox.enabled=true")
                .withBean(javax.sql.DataSource.class, () -> mock(javax.sql.DataSource.class))
                .withBean(org.springframework.transaction.PlatformTransactionManager.class,
                        StubTransactionManager::new)
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseMessage("itways.activity.outbox.table must name the service's outbox table "
                                + "(lower-case letters, digits and underscores, e.g. auth_activity_outbox); got: null"));
    }
}
