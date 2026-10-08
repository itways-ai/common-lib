package com.itways.annotation;

import com.itways.freemarker.FreeMarkerConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(FreeMarkerConfig.class)
public @interface EnableFreeMarker {
}
