package com.itways.annotation;

import com.itways.common.config.CommonConfig;
import java.lang.annotation.*;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(CommonConfig.class)
public @interface EnableCommon {
}
