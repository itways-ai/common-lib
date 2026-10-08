package com.itways.annotation;

import com.itways.encryption.ConnectorSecretsConfig;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * The {@code ConnectorSecrets} bean from {@code CONNECTOR_SECRETS_KEY} (the
 * key that seals the credentials stored on connectors), required unless
 * {@code itways.connector-secrets.required=false}; see
 * {@link ConnectorSecretsConfig}. For journey-service, which owns the
 * connectors; no other service holds the key.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ConnectorSecretsConfig.class)
public @interface EnableConnectorSecrets {
}
