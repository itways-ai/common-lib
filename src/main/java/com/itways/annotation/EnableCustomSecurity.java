package com.itways.annotation;

import com.itways.security.config.SecurityModuleConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SecurityModuleConfiguration.class)
@EnableCache
public @interface EnableCustomSecurity {
}
