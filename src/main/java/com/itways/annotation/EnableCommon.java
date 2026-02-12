package com.itways.annotation;

import com.itways.common.config.CommonModuleConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(CommonModuleConfiguration.class)
public @interface EnableCommon {
}
