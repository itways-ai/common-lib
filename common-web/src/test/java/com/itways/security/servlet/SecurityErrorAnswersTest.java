package com.itways.security.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itways.security.config.SecurityErrorHandlingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/** PLT-14: the shared 401 AUTH_401 / 403 AUTH_403 answers and how they are offered. */
class SecurityErrorAnswersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void theEntryPointAnswers401InTheEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiResponseAuthenticationEntryPoint(null).commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("anonymous"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("error");
        assertThat(body.get("errorCode").asText()).isEqualTo("AUTH_401");
        assertThat(body.get("message").asText()).isEqualTo("Authentication is required");
        assertThat(body.get("timestamp").isTextual()).isTrue();
        assertThat(body.get("data").isNull()).isTrue();
    }

    @Test
    void theDeniedHandlerAnswers403InTheEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiResponseAccessDeniedHandler(new ObjectMapper().findAndRegisterModules(), "Not for API keys")
                .handle(new MockHttpServletRequest(), response, new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("errorCode").asText()).isEqualTo("AUTH_403");
        assertThat(body.get("message").asText()).isEqualTo("Not for API keys");
    }

    /** PLT-28: one wording for the generic 401/403, whichever layer answers. */
    @Test
    void theGenericWordingIsTheSameInEveryLayer() throws Exception {
        assertThat(ApiResponseAuthenticationEntryPoint.DEFAULT_MESSAGE).isEqualTo("Authentication is required")
                .isEqualTo(com.itways.security.core.SecurityMessages.AUTHENTICATION_REQUIRED);
        assertThat(ApiResponseAccessDeniedHandler.DEFAULT_MESSAGE)
                .isEqualTo("You do not have permission to perform this action")
                .isEqualTo(com.itways.security.core.SecurityMessages.PERMISSION_DENIED);

        MockHttpServletResponse denied = new MockHttpServletResponse();
        new ApiResponseAccessDeniedHandler(null).handle(new MockHttpServletRequest(), denied,
                new AccessDeniedException("no"));
        assertThat(mapper.readTree(denied.getContentAsString()).get("message").asText())
                .isEqualTo(com.itways.security.core.SecurityMessages.PERMISSION_DENIED);

        // @PreAuthorize denials and authentication failures that reach the MVC advice.
        com.itways.common.handler.GlobalExceptionHandler advice = new com.itways.common.handler.GlobalExceptionHandler();
        var forbidden = advice.handleAccessDenied(new AccessDeniedException("no"));
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(forbidden.getBody().getErrorCode()).isEqualTo("AUTH_403");
        assertThat(forbidden.getBody().getMessage()).isEqualTo(ApiResponseAccessDeniedHandler.DEFAULT_MESSAGE);
        var unauthorized = advice.handleAuthentication(new InsufficientAuthenticationException("anonymous"));
        assertThat(unauthorized.getStatusCode().value()).isEqualTo(401);
        assertThat(unauthorized.getBody().getErrorCode()).isEqualTo("AUTH_401");
        assertThat(unauthorized.getBody().getMessage()).isEqualTo(ApiResponseAuthenticationEntryPoint.DEFAULT_MESSAGE);
    }

    @Test
    void aCommittedResponseIsLeftAlone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.getWriter().write("data: streaming");
        response.flushBuffer();

        new ApiResponseAuthenticationEntryPoint(null).commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("late"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("data: streaming");
    }

    @Test
    void securityOffersBothAsBeansInAServletService() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(SecurityErrorHandlingConfig.class).run(context -> {
                    assertThat(context).hasSingleBean(AuthenticationEntryPoint.class);
                    assertThat(context.getBean(AuthenticationEntryPoint.class))
                            .isInstanceOf(ApiResponseAuthenticationEntryPoint.class);
                    assertThat(context.getBean(AccessDeniedHandler.class))
                            .isInstanceOf(ApiResponseAccessDeniedHandler.class);
                });
    }

    @Test
    void aServiceOwnEntryPointWins() {
        new WebApplicationContextRunner().withUserConfiguration(OwnEntryPoint.class, SecurityErrorHandlingConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthenticationEntryPoint.class);
                    assertThat(context.getBean(AuthenticationEntryPoint.class))
                            .isInstanceOf(HttpStatusEntryPoint.class);
                    assertThat(context).hasSingleBean(AccessDeniedHandler.class);
                });
    }

    @Test
    void nothingOutsideAServletApplication() {
        new ApplicationContextRunner().withUserConfiguration(SecurityErrorHandlingConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(AuthenticationEntryPoint.class)
                        .doesNotHaveBean(AccessDeniedHandler.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnEntryPoint {
        @Bean
        AuthenticationEntryPoint ownEntryPoint() {
            return new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        }
    }
}
