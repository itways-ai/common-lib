package com.itways.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.itways.encryption.MailSecretsConfig;

/**
 * The {@code MailSecrets} bean from {@code MAIL_SECRETS_KEY} (the key that seals
 * SEND_MAIL SMTP passwords), required unless
 * {@code itways.mail-secrets.required=false}; see {@link MailSecretsConfig}.
 * For the services that seal or open those passwords (journey, notification,
 * speech).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(MailSecretsConfig.class)
public @interface EnableMailSecrets {
}
