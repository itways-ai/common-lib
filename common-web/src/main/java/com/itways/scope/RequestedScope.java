package com.itways.scope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link ListScope} controller parameter: what the request asked to
 * list. Resolved from the {@code scope} query parameter ({@code all},
 * {@code shared} or an assistant id), else the {@link ScopeHeaders#ASSISTANT}
 * header (that assistant's rows plus the shared ones), else the whole account.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestedScope {
}
