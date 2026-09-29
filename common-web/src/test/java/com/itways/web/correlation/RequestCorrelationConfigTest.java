package com.itways.web.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itways.annotation.EnableCommon;
import com.itways.annotation.EnableRequestCorrelation;
import com.itways.common.correlation.RequestIds;
import com.itways.common.exception.BusinessException;
import com.itways.common.handler.GlobalExceptionHandler;
import com.itways.security.internal.InternalEndpointGuardConfig;

import jakarta.servlet.DispatcherType;

/** How the request-id filter is registered (ARC-25), and the whole servlet path through MockMvc. */
class RequestCorrelationConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableRequestCorrelation
    static class Alone {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCommon
    static class WithCommon {
    }

    @AfterEach
    void cleanThread() {
        MDC.clear();
    }

    @Test
    void registeredFirstOfAllFiltersOnEveryDispatch() {
        new WebApplicationContextRunner().withUserConfiguration(Alone.class).run(context -> {
            assertThat(context).hasNotFailed().hasBean("requestCorrelationConfig").hasBean("requestIdFilter");
            FilterRegistrationBean<?> registration = context.getBean("requestIdFilter", FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(RequestIdFilter.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE)
                    .isLessThan(InternalEndpointGuardConfig.ORDER);
            assertThat(registration.getUrlPatterns()).containsExactly("/*");
            assertThat(ReflectionTestUtils.getField(registration, "name")).isEqualTo("requestIdFilter");
            assertThat(ReflectionTestUtils.getField(registration, "dispatcherTypes"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.collection(DispatcherType.class))
                    .containsExactlyInAnyOrder(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        });
    }

    @Test
    void partOfEnableCommon() {
        new WebApplicationContextRunner().withUserConfiguration(WithCommon.class)
                .run(context -> assertThat(context).hasNotFailed().hasBean("requestIdFilter"));
    }

    @Test
    void offWhenDisabled() {
        for (Class<?> app : new Class<?>[] { Alone.class, WithCommon.class }) {
            new WebApplicationContextRunner().withUserConfiguration(app)
                    .withPropertyValues("itways.request-id.enabled=false")
                    .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("requestIdFilter"));
        }
    }

    @Test
    void nothingOutsideAServletApplication() {
        new ApplicationContextRunner().withUserConfiguration(Alone.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("requestIdFilter"));
    }

    // ── The servlet path end to end ────────────────────────────────────────────

    @RestController
    static class Endpoints {

        @GetMapping("/ok")
        String ok() {
            return MDC.get(RequestIds.MDC_KEY);
        }

        @GetMapping("/missing")
        String missing() {
            throw new BusinessException("Journey not found", "JOURNEY_NOT_FOUND", 404);
        }

        @GetMapping("/broken")
        String broken() {
            throw new IllegalStateException("db down");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Endpoints())
            .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();

    @Test
    void theIdIsEchoedAndLoggedAndQuotedByEveryErrorBody() throws Exception {
        mvc.perform(get("/ok").header(RequestIds.HEADER, "gw-1")).andExpect(status().isOk())
                .andExpect(header().string(RequestIds.HEADER, "gw-1"))
                // The handler ran with the id in its logging context.
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("gw-1"));

        mvc.perform(get("/missing").header(RequestIds.HEADER, "gw-2")).andExpect(status().isNotFound())
                .andExpect(header().string(RequestIds.HEADER, "gw-2"))
                .andExpect(jsonPath("$.errorCode").value("JOURNEY_NOT_FOUND"))
                .andExpect(jsonPath("$.reference").value("gw-2"));

        mvc.perform(get("/broken").header(RequestIds.HEADER, "gw-3")).andExpect(status().isInternalServerError())
                .andExpect(header().string(RequestIds.HEADER, "gw-3"))
                .andExpect(jsonPath("$.message").value("Internal server error (reference gw-3)"))
                .andExpect(jsonPath("$.reference").value("gw-3"));

        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    @Test
    void aRequestWithoutAnIdGetsOneAndItsBodyQuotesIt() throws Exception {
        MvcResult result = mvc.perform(get("/missing")).andExpect(status().isNotFound()).andReturn();

        String id = result.getResponse().getHeader(RequestIds.HEADER);
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(result.getResponse().getContentAsString()).contains("\"reference\":\"" + id + "\"");
    }
}
