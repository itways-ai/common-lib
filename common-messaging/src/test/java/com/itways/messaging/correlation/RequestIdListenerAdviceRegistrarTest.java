package com.itways.messaging.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import com.itways.messaging.RabbitPublishingAutoConfiguration;
import com.itways.messaging.RabbitPublishingDefaults;
import java.util.Collection;
import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.config.DirectRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.test.util.ReflectionTestUtils;

/** Where the request-id advice and post-processor are wired (ARC-25); nothing connects to a broker. */
class RequestIdListenerAdviceRegistrarTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> new RabbitPublishingDefaults().postProcessEnvironment(context.getEnvironment(),
                    null))
            .withConfiguration(
                    AutoConfigurations.of(RabbitPublishingAutoConfiguration.class, RabbitAutoConfiguration.class));

    @Test
    void theAdviceGoesInFrontAndEveryExistingAdviceStays() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        RetryOperationsInterceptor retry = RetryInterceptorBuilder.stateless().maxAttempts(3).build();
        factory.setAdviceChain(retry);
        RequestIdListenerAdvice advice = new RequestIdListenerAdvice();

        RequestIdListenerAdviceRegistrar.register(factory, advice);
        RequestIdListenerAdviceRegistrar.register(factory, new RequestIdListenerAdvice()); // once only

        assertThat(factory.getAdviceChain()).containsExactly(advice, retry);
    }

    @Test
    void aFactoryWithoutAChainGetsOne() {
        DirectRabbitListenerContainerFactory factory = new DirectRabbitListenerContainerFactory();

        Object processed = new RequestIdListenerAdviceRegistrar().postProcessAfterInitialization(factory, "direct");

        assertThat(processed).isSameAs(factory);
        assertThat(factory.getAdviceChain()).hasSize(1).allMatch(RequestIdListenerAdvice.class::isInstance);
        assertThat(new RequestIdListenerAdviceRegistrar().postProcessAfterInitialization("other", "other"))
                .isEqualTo("other");
    }

    @Test
    void bootsFactoryAndTemplateGetItByDefault() {
        runner.withPropertyValues("spring.rabbitmq.listener.simple.retry.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasBean("requestIdPublishing").hasBean("requestIdListenerAdvice")
                    .hasBean("requestIdListenerAdviceRegistrar");

            SimpleRabbitListenerContainerFactory factory = context.getBean("rabbitListenerContainerFactory",
                    SimpleRabbitListenerContainerFactory.class);
            Advice[] chain = factory.getAdviceChain();
            assertThat(chain).hasSize(2);
            assertThat(chain[0]).isInstanceOf(RequestIdListenerAdvice.class);
            // Boot's retry interceptor is still there.
            assertThat(chain[1]).isInstanceOf(RetryOperationsInterceptor.class);

            assertThat(beforePublish(context.getBean(RabbitTemplate.class)))
                    .anyMatch(RequestIdPublishPostProcessor.class::isInstance);
            // The confirm and returns callbacks of PLT-30 are untouched.
            assertThat(ReflectionTestUtils.getField(context.getBean(RabbitTemplate.class), "confirmCallback"))
                    .isNotNull();
        });
    }

    @Test
    void aServiceOwnFactoryGetsItToo() {
        runner.withUserConfiguration(OwnFactory.class).run(context -> {
            SimpleRabbitListenerContainerFactory own = context.getBean("ownFactory",
                    SimpleRabbitListenerContainerFactory.class);
            assertThat(own.getAdviceChain()).hasSize(2);
            assertThat(own.getAdviceChain()[0]).isInstanceOf(RequestIdListenerAdvice.class);
        });
    }

    @Test
    void offWhenDisabled() {
        runner.withPropertyValues("itways.request-id.messaging.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean("requestIdPublishing")
                    .doesNotHaveBean("requestIdListenerAdvice").doesNotHaveBean("requestIdListenerAdviceRegistrar");
            SimpleRabbitListenerContainerFactory factory = context.getBean("rabbitListenerContainerFactory",
                    SimpleRabbitListenerContainerFactory.class);
            Advice[] chain = factory.getAdviceChain();
            assertThat(chain == null ? new Advice[0] : chain).noneMatch(RequestIdListenerAdvice.class::isInstance);
            Collection<MessagePostProcessor> processors = beforePublish(context.getBean(RabbitTemplate.class));
            assertThat(processors == null ? java.util.List.of() : processors)
                    .noneMatch(RequestIdPublishPostProcessor.class::isInstance);
        });
    }

    @SuppressWarnings("unchecked")
    private static Collection<MessagePostProcessor> beforePublish(RabbitTemplate template) {
        return (Collection<MessagePostProcessor>) ReflectionTestUtils.getField(template,
                "beforePublishPostProcessors");
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnFactory {
        @Bean
        SimpleRabbitListenerContainerFactory ownFactory() {
            SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
            factory.setAdviceChain(RetryInterceptorBuilder.stateless().maxAttempts(2).build());
            return factory;
        }
    }
}
