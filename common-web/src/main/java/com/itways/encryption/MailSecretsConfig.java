package com.itways.encryption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import lombok.extern.slf4j.Slf4j;

/**
 * What {@code @EnableMailSecrets} brings: the {@link MailSecrets} bean built
 * from {@code mail.secrets.key} (default: the {@code MAIL_SECRETS_KEY}
 * environment variable) and {@code mail.secrets.previous-key} (default
 * {@code MAIL_SECRETS_KEY_PREVIOUS}, set only during a rotation). ARC-11; the
 * one version of journey-service's and notification-service's
 * {@code MailSecretsConfig} and speech-service's inline construction.
 *
 * <p>
 * By default the key is required: with a blank key the service does not start
 * ({@link MailSecrets}'s own message names the variable), because every step
 * save seals with it. A service for which the key is optional sets
 * {@code itways.mail-secrets.required=false}: with a blank key no bean is
 * registered (a WARN says so) and consumers take
 * {@code ObjectProvider<MailSecrets>}; with a key the bean is built as usual.
 * A service that declares its own {@link MailSecrets} bean keeps it.
 */
@Slf4j
@Configuration(value = "mailSecretsConfig", proxyBeanMethods = false)
public class MailSecretsConfig {

    public static final String KEY_PROPERTY = "mail.secrets.key";
    public static final String KEY_VARIABLE = "MAIL_SECRETS_KEY";
    public static final String PREVIOUS_KEY_PROPERTY = "mail.secrets.previous-key";
    public static final String PREVIOUS_KEY_VARIABLE = "MAIL_SECRETS_KEY_PREVIOUS";
    public static final String REQUIRED_PROPERTY = "itways.mail-secrets.required";

    @Bean
    @ConditionalOnMissingBean(MailSecrets.class)
    @Conditional(KeyPresentOrRequired.class)
    public MailSecrets mailSecrets(@Value("${" + KEY_PROPERTY + ":${" + KEY_VARIABLE + ":}}") String key,
            @Value("${" + PREVIOUS_KEY_PROPERTY + ":${" + PREVIOUS_KEY_VARIABLE + ":}}") String previousKey) {
        return new MailSecrets(key, previousKey);
    }

    /** The configured key: the property, else the environment variable, else blank. */
    static String configuredKey(Environment environment) {
        String key = environment.getProperty(KEY_PROPERTY);
        if (key == null || key.isBlank()) {
            key = environment.getProperty(KEY_VARIABLE, "");
        }
        return key == null ? "" : key;
    }

    /**
     * Registers the bean when the key is required (a blank one then fails the
     * startup inside the bean method) or when a key is present; skips it, with
     * a warning, when the key is optional and blank.
     */
    static class KeyPresentOrRequired implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            if (environment.getProperty(REQUIRED_PROPERTY, Boolean.class, true)) {
                return true;
            }
            if (!configuredKey(environment).isBlank()) {
                return true;
            }
            log.warn("{} is not set and {}=false: no MailSecrets bean; SEND_MAIL passwords cannot be sealed "
                    + "or opened until it is", KEY_VARIABLE, REQUIRED_PROPERTY);
            return false;
        }
    }
}
