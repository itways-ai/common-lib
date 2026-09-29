package com.itways.notification.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    private String recipient;
    private String subject;
    /** Carries sign-in codes and reset links: never printed. */
    @ToString.Exclude
    private String body;
    private Map<String, Object> metadata;
    /** A tenant's SMTP settings, password included: never printed. */
    @ToString.Exclude
    private EmailProviderConfig providerConfig;
    private String type; // EMAIL, SMS, PUSH

    // Constants for RabbitMQ (Defining here to be shared)
    public static final String QUEUE_NAME = "notification.queue";
    public static final String EXCHANGE_NAME = "notification.exchange";
    public static final String ROUTING_KEY = "notification.routing.key";
}
