package com.itways.security.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.itways.annotation.EnableInternalEndpointGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.test.util.ReflectionTestUtils;

/** How {@code @EnableInternalEndpointGuard} registers the filter, and the knobs. */
class InternalEndpointGuardConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableInternalEndpointGuard
    static class App {

        /** What {@code @EnableCustomSecurity} would bring. */
        @Bean
        InternalServiceToken internalServiceToken() {
            return new InternalServiceToken("svc-token-test", false);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableInternalEndpointGuard
    static class WithoutTheToken {
    }

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(App.class);

    @Test
    void registeredByDefaultAheadOfSpringSecurityButBehindTheFirstSlot() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasBean("internalEndpointGuardFilter");
            FilterRegistrationBean<?> registration = context.getBean("internalEndpointGuardFilter",
                    FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(InternalEndpointGuard.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10)
                    .isEqualTo(InternalEndpointGuardConfig.ORDER);
            assertThat(registration.getUrlPatterns()).containsExactly("/*");
            assertThat(ReflectionTestUtils.getField(registration, "name")).isEqualTo("internalEndpointGuard");
            assertThat(((InternalEndpointGuard) registration.getFilter()).proxyHeaders())
                    .containsExactly("X-Forwarded-For", "X-Forwarded-Host", "Forwarded");
        });
    }

    @Test
    void offWhenDisabled() {
        runner.withPropertyValues("itways.internal-guard.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("internalEndpointGuardFilter"));
    }

    @Test
    void theProxyHeadersAreAKnob() {
        runner.withPropertyValues("itways.internal-guard.proxy-headers= X-Real-IP , Forwarded").run(context -> {
            FilterRegistrationBean<?> registration = context.getBean("internalEndpointGuardFilter",
                    FilterRegistrationBean.class);
            assertThat(((InternalEndpointGuard) registration.getFilter()).proxyHeaders())
                    .containsExactly("X-Real-IP", "Forwarded");
        });
    }

    @Test
    void nothingOutsideAServletApplication() {
        new ApplicationContextRunner().withUserConfiguration(App.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("internalEndpointGuardFilter"));
    }

    @Test
    void withoutTheServiceTokenBeanTheServiceDoesNotStart() {
        // Rather than a guard that silently checks nothing: @EnableCustomSecurity is required.
        new WebApplicationContextRunner().withUserConfiguration(WithoutTheToken.class)
                .run(context -> assertThat(context).hasFailed());
    }
}
