package com.itways.messaging.correlation;

import java.util.Arrays;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Adds the {@link RequestIdListenerAdvice} to every
 * {@link AbstractRabbitListenerContainerFactory} bean (Spring Boot's
 * {@code rabbitListenerContainerFactory} and any a service declares), so every
 * {@code @RabbitListener} runs with the request id of its message in the
 * logging context (ARC-25).
 *
 * <p>
 * The factory's existing advice chain is kept (Spring Boot's retry
 * interceptor, a service's own advice); the request-id advice goes in front of
 * it, as the outermost one, so the retry attempts and the recoverer's log lines
 * after the last attempt carry the id too. A factory whose chain already holds
 * a {@code RequestIdListenerAdvice} is left alone.
 *
 * <p>
 * Registered by {@code RabbitPublishingAutoConfiguration} (static, as a
 * post-processor must be) unless {@code itways.request-id.messaging.enabled=false}.
 */
public class RequestIdListenerAdviceRegistrar implements BeanPostProcessor {

    private final RequestIdListenerAdvice advice;

    public RequestIdListenerAdviceRegistrar() {
        this(new RequestIdListenerAdvice());
    }

    public RequestIdListenerAdviceRegistrar(RequestIdListenerAdvice advice) {
        this.advice = advice;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof AbstractRabbitListenerContainerFactory<?> factory) {
            register(factory, advice);
        }
        return bean;
    }

    /**
     * Puts {@code advice} in front of {@code factory}'s advice chain, keeping
     * every advice already there; nothing when the chain has a
     * {@link RequestIdListenerAdvice} already.
     */
    public static void register(AbstractRabbitListenerContainerFactory<?> factory, RequestIdListenerAdvice advice) {
        Advice[] existing = factory.getAdviceChain();
        if (existing == null) {
            existing = new Advice[0];
        }
        if (Arrays.stream(existing).anyMatch(RequestIdListenerAdvice.class::isInstance)) {
            return;
        }
        Advice[] chain = new Advice[existing.length + 1];
        chain[0] = advice;
        System.arraycopy(existing, 0, chain, 1, existing.length);
        factory.setAdviceChain(chain);
    }
}
