package com.itways.annotation;

import com.itways.notification.config.NotificationConfig;
import java.lang.annotation.*;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(NotificationConfig.class)
public @interface EnableNotifications {
}
