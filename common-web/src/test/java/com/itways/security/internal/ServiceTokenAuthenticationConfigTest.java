package com.itways.security.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * The service-token filter is a bean only when {@code itways.internal-token} is set, and it is
 * never registered with the servlet container on its own: it runs where a service's chain puts
 * it.
 */
class ServiceTokenAuthenticationConfigTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withBean(InternalServiceToken.class, () -> new InternalServiceToken("svc-token", false))
            .withUserConfiguration(ServiceTokenAuthenticationConfig.class);

    @Test
    void withATokenTheFilterIsABeanThatTheContainerDoesNotRun() {
        runner.withPropertyValues("itways.internal-token=svc-token").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ServiceTokenAuthenticationFilter.class)
                    .hasBean(ServiceTokenAuthenticationConfig.FILTER_BEAN_NAME);
            FilterRegistrationBean<?> registration = context.getBean(
                    ServiceTokenAuthenticationConfig.REGISTRATION_BEAN_NAME, FilterRegistrationBean.class);
            assertThat(registration.isEnabled()).isFalse();
            assertThat(registration.getFilter())
                    .isSameAs(context.getBean(ServiceTokenAuthenticationConfig.FILTER_BEAN_NAME));
        });
    }

    @Test
    void withoutATokenThereIsNoFilter() {
        runner.run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(ServiceTokenAuthenticationFilter.class));
        runner.withPropertyValues("itways.internal-token=").run(context -> assertThat(context)
                .doesNotHaveBean(ServiceTokenAuthenticationFilter.class));
        runner.withPropertyValues("itways.internal-token=   ").run(context -> assertThat(context)
                .doesNotHaveBean(ServiceTokenAuthenticationFilter.class));
        // The compose placeholder with the variable unset.
        runner.withPropertyValues("itways.internal-token=${INTERNAL_SERVICE_TOKEN_UNSET_IN_TEST:}")
                .run(context -> assertThat(context).doesNotHaveBean(ServiceTokenAuthenticationFilter.class));
    }
}
