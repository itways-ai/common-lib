package com.itways.annotation;

import com.itways.security.config.SecurityConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SecurityConfig.class)
@EnableCache
public @interface EnableCustomSecurity {
}
