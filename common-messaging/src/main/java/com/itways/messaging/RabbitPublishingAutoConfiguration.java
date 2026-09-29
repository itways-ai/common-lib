package com.itways.messaging;

import java.util.Set;

import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import com.itways.contracts.account.AccountEvents;

import lombok.extern.slf4j.Slf4j;

/**
 * What a service's {@code RabbitTemplate} does with a nack or a returned message
 * (PLT-30): it logs it. With {@link RabbitPublishingDefaults} the broker
 * confirms every publish and returns what it cannot route; this customizer
 * gives Boot's template the one confirm callback and the one returns callback a
 * template may have, so neither outcome is silent any more.
 *
 * <p>
 * Publishers that must not lose a message wait for the confirm themselves: the
 * activity outbox relay ({@code RabbitConfirmedSender}) keeps its rows until
 * every message of a batch is acked and not returned. The fire-and-forget
 * publishers (notification requests, {@code account.events}, dead-letter
 * republishing) rely on these log lines.
 *
 * <p>
 * An exchange where "nobody is listening" is normal is listed in
 * {@value #QUIET_EXCHANGES_PROPERTY} (default {@value AccountEvents#EXCHANGE}:
 * a routing key without a subscriber, such as {@code assistant.created}); its
 * returns are logged at debug instead of warn.
 */
@Slf4j
@AutoConfiguration(before = RabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitPublishingAutoConfiguration {

    /** Comma-separated exchanges whose unroutable messages are expected. */
    public static final String QUIET_EXCHANGES_PROPERTY = "itways.rabbitmq.returns.quiet-exchanges";

    @Bean
    @ConditionalOnMissingBean(name = "publishOutcomeLogging")
    public RabbitTemplateCustomizer publishOutcomeLogging(Environment environment) {
        Set<String> quiet = StringUtils.commaDelimitedListToSet(
                environment.getProperty(QUIET_EXCHANGES_PROPERTY, AccountEvents.EXCHANGE));
        return template -> {
            template.setConfirmCallback(RabbitPublishingAutoConfiguration::logNack);
            template.setReturnsCallback(returned -> logReturn(returned, quiet));
        };
    }

    static void logNack(CorrelationData correlation, boolean ack, String cause) {
        if (!ack) {
            log.warn("[RABBIT] The broker refused (nack) a published message{}: {}",
                    correlation != null ? " " + correlation.getId() : "", cause);
        }
    }

    static void logReturn(ReturnedMessage returned, Set<String> quiet) {
        String messageId = returned.getMessage().getMessageProperties().getMessageId();
        if (quiet.contains(returned.getExchange())) {
            log.debug("[RABBIT] No queue is bound for {} / {}; message not delivered ({} {})", returned.getExchange(),
                    returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText());
            return;
        }
        log.warn("[RABBIT] Message{} to exchange '{}' with routing key '{}' was returned unrouted: {} {}",
                messageId != null ? " " + messageId : "", returned.getExchange(), returned.getRoutingKey(),
                returned.getReplyCode(), returned.getReplyText());
    }
}
