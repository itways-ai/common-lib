package com.itways.annotation;

import com.itways.common.config.CommonConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(CommonConfig.class)
public @interface EnableCommon {
}
