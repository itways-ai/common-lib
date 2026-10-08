package com.itways.encryption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * What {@code @EnableConnectorSecrets} brings: the {@link ConnectorSecrets}
 * bean built from {@code connector.secrets.key} (default: the
 * {@code CONNECTOR_SECRETS_KEY} environment variable) and
 * {@code connector.secrets.previous-key} (default
 * {@code CONNECTOR_SECRETS_KEY_PREVIOUS}, set only during a rotation). The
 * same shape as {@link MailSecretsConfig}, for journey-service, which owns the
 * connectors and their sealed credentials.
 *
 * <p>
 * By default the key is required: with a blank key the service does not start
 * ({@link ConnectorSecrets}'s own message names the variable), because every
 * connector save seals with it and every resolve opens with it. A service for
 * which the key is optional sets {@code itways.connector-secrets.required=false}:
 * with a blank key no bean is registered (a WARN says so) and consumers take
 * {@code ObjectProvider<ConnectorSecrets>}; with a key the bean is built as
 * usual. A service that declares its own {@link ConnectorSecrets} bean keeps it.
 */
@Slf4j
@Configuration(value = "connectorSecretsConfig", proxyBeanMethods = false)
public class ConnectorSecretsConfig {

    public static final String KEY_PROPERTY = "connector.secrets.key";
    public static final String KEY_VARIABLE = "CONNECTOR_SECRETS_KEY";
    public static final String PREVIOUS_KEY_PROPERTY = "connector.secrets.previous-key";
    public static final String PREVIOUS_KEY_VARIABLE = "CONNECTOR_SECRETS_KEY_PREVIOUS";
    public static final String REQUIRED_PROPERTY = "itways.connector-secrets.required";

    @Bean
    @ConditionalOnMissingBean(ConnectorSecrets.class)
    @Conditional(KeyPresentOrRequired.class)
    public ConnectorSecrets connectorSecrets(
            @Value("${" + KEY_PROPERTY + ":${" + KEY_VARIABLE + ":}}") String key,
            @Value("${" + PREVIOUS_KEY_PROPERTY + ":${" + PREVIOUS_KEY_VARIABLE + ":}}") String previousKey) {
        return new ConnectorSecrets(key, previousKey);
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
            log.warn("{} is not set and {}=false: no ConnectorSecrets bean; connector credentials cannot be "
                    + "sealed or opened until it is", KEY_VARIABLE, REQUIRED_PROPERTY);
            return false;
        }
    }
}
