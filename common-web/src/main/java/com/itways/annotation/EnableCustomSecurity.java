package com.itways.annotation;

import com.itways.security.config.SecurityConfig;
import java.lang.annotation.*;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SecurityConfig.class)
@EnableCache
public @interface EnableCustomSecurity {
}
