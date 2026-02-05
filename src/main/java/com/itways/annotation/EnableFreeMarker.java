package com.itways.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.itways.freemarker.FreeMarkerAutoConfiguration;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(FreeMarkerAutoConfiguration.class)
public @interface EnableFreeMarker {
}
