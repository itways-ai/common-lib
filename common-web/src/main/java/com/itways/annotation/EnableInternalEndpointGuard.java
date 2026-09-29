package com.itways.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.itways.security.internal.InternalEndpointGuardConfig;

/**
 * Answers every proxied request for an {@code /internal/} path with the real
 * 404, and checks the service token on direct ones; see
 * {@link com.itways.security.internal.InternalEndpointGuard}. For services
 * that expose routes only other platform services may call. Needs
 * {@code @EnableCustomSecurity} (the {@code InternalServiceToken} bean); not
 * part of it, so a service without internal routes registers no filter.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(InternalEndpointGuardConfig.class)
public @interface EnableInternalEndpointGuard {
}
