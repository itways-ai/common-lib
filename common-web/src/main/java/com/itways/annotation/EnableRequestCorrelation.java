package com.itways.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.itways.web.correlation.RequestCorrelationConfig;

/**
 * Gives every request a request id ({@code X-Request-Id}) that goes to the
 * logs, the response and the error bodies; see
 * {@link com.itways.web.correlation.RequestIdFilter}. Already part of
 * {@code @EnableCommon}: for a servlet service without it.
 * {@code itways.request-id.enabled=false} switches it off.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RequestCorrelationConfig.class)
public @interface EnableRequestCorrelation {
}
