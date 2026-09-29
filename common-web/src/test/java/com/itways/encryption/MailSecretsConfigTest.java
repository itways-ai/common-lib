package com.itways.encryption;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.itways.annotation.EnableMailSecrets;

/** How {@code @EnableMailSecrets} builds the bean, when it fails, and when it stays away. */
@ExtendWith(OutputCaptureExtension.class)
class MailSecretsConfigTest {

    private static final String KEY_A = MailSecretsTest.randomKey();
    private static final String KEY_B = MailSecretsTest.randomKey();

    @Configuration(proxyBeanMethods = false)
    @EnableMailSecrets
    static class App {
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnBean {

        @Bean
        MailSecrets mailSecrets() {
            return new MailSecrets(KEY_B, null);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(App.class);

    @Test
    void requiredByDefaultABlankKeyFailsTheStartup() {
        runner.run(context -> assertThat(context).getFailure().rootCause()
                .hasMessageContaining("MAIL_SECRETS_KEY is not set"));
        runner.withPropertyValues("mail.secrets.key=   ").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void aKeyBuildsTheBean() {
        runner.withPropertyValues("mail.secrets.key=" + KEY_A).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(MailSecrets.class).hasBean("mailSecrets");
            MailSecrets secrets = context.getBean(MailSecrets.class);
            assertThat(secrets.open(secrets.seal("smtp-p@ss"))).isEqualTo("smtp-p@ss");
        });
    }

    @Test
    void theEnvironmentVariablesAreTheFallback() {
        String underB = new MailSecrets(KEY_B, null).seal("smtp-p@ss");
        runner.withPropertyValues("MAIL_SECRETS_KEY=" + KEY_A, "MAIL_SECRETS_KEY_PREVIOUS=" + KEY_B).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(MailSecrets.class);
            MailSecrets secrets = context.getBean(MailSecrets.class);
            assertThat(secrets.open(underB)).isEqualTo("smtp-p@ss");
            assertThat(secrets.isCurrent(underB)).isFalse();
        });
    }

    @Test
    void optionalAndBlankRegistersNoBean(CapturedOutput output) {
        runner.withPropertyValues("itways.mail-secrets.required=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(MailSecrets.class);
            assertThat(output.getAll()).contains("MAIL_SECRETS_KEY is not set and itways.mail-secrets.required=false");
        });
    }

    @Test
    void optionalWithAKeyBuildsTheBean() {
        runner.withPropertyValues("itways.mail-secrets.required=false", "mail.secrets.key=" + KEY_A)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(MailSecrets.class));
        runner.withPropertyValues("itways.mail-secrets.required=false", "MAIL_SECRETS_KEY=" + KEY_A)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(MailSecrets.class));
    }

    @Test
    void aServiceOwnBeanWins() {
        String underB = new MailSecrets(KEY_B, null).seal("smtp-p@ss");
        new ApplicationContextRunner().withUserConfiguration(OwnBean.class, App.class)
                .withPropertyValues("mail.secrets.key=" + KEY_A).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(MailSecrets.class);
                    assertThat(context.getBean(MailSecrets.class).open(underB)).isEqualTo("smtp-p@ss");
                });
    }

    @Test
    void aBadKeyFailsWithTheVariableNamed() {
        runner.withPropertyValues("mail.secrets.key=short").run(context -> assertThat(context).getFailure()
                .rootCause().hasMessageContaining("MAIL_SECRETS_KEY"));
    }
}
