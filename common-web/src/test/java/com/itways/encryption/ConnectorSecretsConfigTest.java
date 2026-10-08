package com.itways.encryption;

import static org.assertj.core.api.Assertions.assertThat;

import com.itways.annotation.EnableConnectorSecrets;
import com.itways.annotation.EnableMailSecrets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** How {@code @EnableConnectorSecrets} builds the bean, when it fails, and when it stays away. */
@ExtendWith(OutputCaptureExtension.class)
class ConnectorSecretsConfigTest {

    private static final String KEY_A = MailSecretsTest.randomKey();
    private static final String KEY_B = MailSecretsTest.randomKey();

    @Configuration(proxyBeanMethods = false)
    @EnableConnectorSecrets
    static class App {
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnBean {

        @Bean
        ConnectorSecrets connectorSecrets() {
            return new ConnectorSecrets(KEY_B, null);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConnectorSecrets
    @EnableMailSecrets
    static class Both {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(App.class);

    @Test
    void requiredByDefaultABlankKeyFailsTheStartup() {
        runner.run(context -> assertThat(context).getFailure().rootCause()
                .hasMessageContaining("CONNECTOR_SECRETS_KEY is not set"));
        runner.withPropertyValues("connector.secrets.key=   ").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void aKeyBuildsTheBean() {
        runner.withPropertyValues("connector.secrets.key=" + KEY_A).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ConnectorSecrets.class).hasBean("connectorSecrets");
            ConnectorSecrets secrets = context.getBean(ConnectorSecrets.class);
            assertThat(secrets.open(secrets.seal("sk_live_abc", "inst", "acct", "apiKey"), "inst", "acct", "apiKey"))
                    .isEqualTo("sk_live_abc");
        });
    }

    @Test
    void theEnvironmentVariablesAreTheFallback() {
        String underB = new ConnectorSecrets(KEY_B, null).seal("sk_live_abc", "inst", "acct", "apiKey");
        runner.withPropertyValues("CONNECTOR_SECRETS_KEY=" + KEY_A, "CONNECTOR_SECRETS_KEY_PREVIOUS=" + KEY_B)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ConnectorSecrets.class);
                    ConnectorSecrets secrets = context.getBean(ConnectorSecrets.class);
                    assertThat(secrets.open(underB, "inst", "acct", "apiKey")).isEqualTo("sk_live_abc");
                    assertThat(secrets.isCurrent(underB)).isFalse();
                });
    }

    @Test
    void optionalAndBlankRegistersNoBean(CapturedOutput output) {
        runner.withPropertyValues("itways.connector-secrets.required=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(ConnectorSecrets.class);
            assertThat(output.getAll())
                    .contains("CONNECTOR_SECRETS_KEY is not set and itways.connector-secrets.required=false");
        });
    }

    @Test
    void optionalWithAKeyBuildsTheBean() {
        runner.withPropertyValues("itways.connector-secrets.required=false", "connector.secrets.key=" + KEY_A)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ConnectorSecrets.class));
        runner.withPropertyValues("itways.connector-secrets.required=false", "CONNECTOR_SECRETS_KEY=" + KEY_A)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ConnectorSecrets.class));
    }

    @Test
    void aServiceOwnBeanWins() {
        String underB = new ConnectorSecrets(KEY_B, null).seal("sk_live_abc", "inst", "acct", "apiKey");
        new ApplicationContextRunner().withUserConfiguration(OwnBean.class, App.class)
                .withPropertyValues("connector.secrets.key=" + KEY_A).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ConnectorSecrets.class);
                    assertThat(context.getBean(ConnectorSecrets.class).open(underB, "inst", "acct", "apiKey"))
                            .isEqualTo("sk_live_abc");
                });
    }

    @Test
    void aBadKeyFailsWithTheVariableNamed() {
        runner.withPropertyValues("connector.secrets.key=short").run(context -> assertThat(context).getFailure()
                .rootCause().hasMessageContaining("CONNECTOR_SECRETS_KEY"));
    }

    /** journey-service holds both keys; the two beans are distinct types and do not stand in for each other. */
    @Test
    void mailAndConnectorSecretsLiveSideBySide() {
        new ApplicationContextRunner().withUserConfiguration(Both.class)
                .withPropertyValues("connector.secrets.key=" + KEY_A, "mail.secrets.key=" + KEY_B).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ConnectorSecrets.class)
                            .hasSingleBean(MailSecrets.class).hasBean("connectorSecrets").hasBean("mailSecrets");
                    assertThat(context.getBeansOfType(SealedSecrets.class)).hasSize(2);
                    assertThat(context.getBean(ConnectorSecrets.class).currentKeyId())
                            .isNotEqualTo(context.getBean(MailSecrets.class).currentKeyId());
                });
        // The mail key alone does not stand in for the connector key.
        new ApplicationContextRunner().withUserConfiguration(Both.class).withPropertyValues("mail.secrets.key=" + KEY_B)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("CONNECTOR_SECRETS_KEY is not set"));
    }
}
