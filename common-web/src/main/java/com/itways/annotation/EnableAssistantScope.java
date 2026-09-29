package com.itways.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.itways.scope.AssistantScopeConfig;

/**
 * For services that keep rows per assistant: registers
 * {@link com.itways.scope.AssistantDirectory}, {@link com.itways.scope.ScopeRules}
 * and the {@code @RequestedScope} controller parameter. Needs a JDBC datasource
 * on the shared database.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AssistantScopeConfig.class)
public @interface EnableAssistantScope {
}
