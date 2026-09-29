package com.itways.annotation;

import com.itways.notification.config.NotificationConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(NotificationConfig.class)
public @interface EnableNotifications {
}
