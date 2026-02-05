package com.itways.annotation;

import com.itways.notification.config.NotificationMessagingConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(NotificationMessagingConfig.class)
public @interface EnableNotificationMessaging {
}
