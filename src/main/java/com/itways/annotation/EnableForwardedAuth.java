package com.itways.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.itways.feign.ForwardedAuthFeignConfig;

/**
 * Forwards the caller's {@code Authorization} / {@code X-API-KEY} on every
 * Feign call this service makes. For services that call other Nibras services
 * on behalf of the user; see {@link ForwardedAuthFeignConfig}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ForwardedAuthFeignConfig.class)
public @interface EnableForwardedAuth {
}
