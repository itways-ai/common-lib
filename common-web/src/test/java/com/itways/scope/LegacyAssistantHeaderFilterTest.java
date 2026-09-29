package com.itways.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itways.annotation.EnableAssistantScope;
import com.itways.annotation.EnableCommon;
import com.itways.security.internal.InternalEndpointGuardConfig;
import com.itways.web.correlation.RequestCorrelationConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** The legacy assistant header reaches controllers as {@code X-Assistant-Id} (2.1.0 transition). */
@SuppressWarnings("removal") // the legacy header name is what these tests send
class LegacyAssistantHeaderFilterTest {

    private static final String ID = "5f0c1a4e-0000-4000-8000-000000000001";

    private final LegacyAssistantHeaderFilter filter = new LegacyAssistantHeaderFilter();

    @Test
    void aLegacyOnlyRequestIsSeenWithTheNewHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ScopeHeaders.LEGACY_ASSISTANT, ID);
        request.addHeader("Authorization", "Bearer t");

        HttpServletRequest seen = passThrough(request);

        assertThat(seen.getHeader(ScopeHeaders.ASSISTANT)).isEqualTo(ID);
        assertThat(seen.getHeader("x-assistant-id")).isEqualTo(ID);
        assertThat(Collections.list(seen.getHeaders(ScopeHeaders.ASSISTANT))).containsExactly(ID);
        assertThat(Collections.list(seen.getHeaderNames())).contains(ScopeHeaders.ASSISTANT,
                ScopeHeaders.LEGACY_ASSISTANT, "Authorization");
        // Everything else as sent.
        assertThat(seen.getHeader("Authorization")).isEqualTo("Bearer t");
        assertThat(seen.getHeader(ScopeHeaders.LEGACY_ASSISTANT)).isEqualTo(ID);
    }

    @Test
    void aRequestWithTheNewHeaderIsLeftAlone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ScopeHeaders.ASSISTANT, ID);
        request.addHeader(ScopeHeaders.LEGACY_ASSISTANT, "5f0c1a4e-0000-4000-8000-000000000002");

        assertThat(passThrough(request)).isSameAs(request);
    }

    @Test
    void aRequestWithoutEitherIsLeftAlone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        HttpServletRequest seen = passThrough(request);

        assertThat(seen).isSameAs(request);
        assertThat(seen.getHeader(ScopeHeaders.ASSISTANT)).isNull();
    }

    private HttpServletRequest passThrough(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) chain.getRequest();
    }

    // ── A controller that reads the constant ─────────────────────────────────

    @RestController
    static class Endpoints {

        @GetMapping("/selected")
        String selected(@RequestHeader(value = ScopeHeaders.ASSISTANT, required = false) String assistant) {
            return String.valueOf(assistant);
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Endpoints()).addFilters(filter).build();

    @Test
    void aControllerReadingTheConstantAcceptsBothNames() throws Exception {
        mvc.perform(get("/selected").header(ScopeHeaders.ASSISTANT, ID)).andExpect(status().isOk())
                .andExpect(content().string(ID));
        mvc.perform(get("/selected").header(ScopeHeaders.LEGACY_ASSISTANT, ID)).andExpect(status().isOk())
                .andExpect(content().string(ID));
        mvc.perform(get("/selected")).andExpect(status().isOk()).andExpect(content().string("null"));
    }

    // ── Registration ─────────────────────────────────────────────────────────

    @Configuration(proxyBeanMethods = false)
    @EnableCommon
    static class WithCommon {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCommon
    @EnableAssistantScope
    static class WithCommonAndScope {

        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }
    }

    @Test
    void registeredOnceByEnableCommonAndEnableAssistantScope() {
        for (Class<?> app : new Class<?>[] { WithCommon.class, WithCommonAndScope.class }) {
            new WebApplicationContextRunner().withUserConfiguration(app).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBeansOfType(FilterRegistrationBean.class).values())
                        .filteredOn(r -> r.getFilter() instanceof LegacyAssistantHeaderFilter).hasSize(1);
                FilterRegistrationBean<?> registration = context.getBean(LegacyAssistantHeaderConfig.FILTER_NAME,
                        FilterRegistrationBean.class);
                assertThat(registration.getUrlPatterns()).containsExactly("/*");
                assertThat(registration.getOrder()).isGreaterThan(RequestCorrelationConfig.ORDER)
                        .isLessThan(InternalEndpointGuardConfig.ORDER);
            });
        }
    }

    @Test
    void nothingOutsideAServletApplication() {
        new ApplicationContextRunner().withUserConfiguration(LegacyAssistantHeaderConfig.class)
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(LegacyAssistantHeaderConfig.FILTER_NAME));
    }
}
