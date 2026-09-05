package com.itways.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.itways.jpa.AccountAuditingConfig;

/**
 * Turns on JPA auditing with the calling account as the auditor. For services
 * with Spring Data JPA whose rows carry created_by / updated_by; see
 * {@link AccountAuditingConfig}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AccountAuditingConfig.class)
public @interface EnableAccountAuditing {
}
