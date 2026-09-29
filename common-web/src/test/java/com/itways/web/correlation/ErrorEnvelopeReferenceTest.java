package com.itways.web.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.itways.common.correlation.RequestIds;
import com.itways.common.exception.BusinessException;
import com.itways.common.handler.CustomErrorController;
import com.itways.common.handler.DataAccessExceptionHandler;
import com.itways.common.handler.GlobalExceptionHandler;
import com.itways.common.response.ApiResponse;
import com.itways.security.internal.InternalEndpointGuard;
import com.itways.security.internal.InternalServiceToken;
import com.itways.security.servlet.ApiResponseAccessDeniedHandler;
import com.itways.security.servlet.ApiResponseAuthenticationEntryPoint;

import jakarta.servlet.RequestDispatcher;

/**
 * Every error envelope the library writes quotes the request id as
 * {@code reference} when the logging context holds one, and leaves the field
 * out otherwise (ARC-25). Success envelopes never carry it.
 */
class ErrorEnvelopeReferenceTest {

    private static final String ID = "req-7c1e";
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @AfterEach
    void cleanThread() {
        MDC.clear();
    }

    /** Each writer, producing its JSON body. */
    private static Map<String, Supplier<String>> writers() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        GlobalExceptionHandler hiding = new GlobalExceptionHandler(true);
        return Map.ofEntries(
                Map.entry("business 4xx", () -> json(handler.handleBusinessException(
                        new BusinessException("Not yours", "FORBIDDEN", 403)))),
                Map.entry("business 5xx", () -> json(handler.handleBusinessException(
                        new BusinessException("try again", "EXTERNAL_SERVICE_ERROR", 503)))),
                Map.entry("business 5xx hidden", () -> json(hiding.handleBusinessException(
                        new BusinessException("secret", "X", 500)))),
                Map.entry("fixed-text error hook", () -> json(handler.handleAccessDenied(
                        new AccessDeniedException("no")))),
                Map.entry("validation", () -> json(handler.handleConstraintViolation(
                        new jakarta.validation.ConstraintViolationException(Set.of())))),
                Map.entry("method not allowed", () -> json(handler.handleMethodNotSupported(
                        new HttpRequestMethodNotSupportedException("PATCH", List.of("GET"))))),
                Map.entry("catch-all 500", () -> json(handler.handleGeneralException(new IllegalStateException()))),
                Map.entry("data conflict", () -> json(new DataAccessExceptionHandler().handleDataIntegrity(
                        new DataIntegrityViolationException("duplicate key")))),
                Map.entry("error page", () -> {
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
                    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
                    return json(new CustomErrorController().handleError(request));
                }),
                Map.entry("401 entry point", () -> write(response -> new ApiResponseAuthenticationEntryPoint(null)
                        .commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("x")))),
                Map.entry("403 denied handler", () -> write(response -> new ApiResponseAccessDeniedHandler(null)
                        .handle(new MockHttpServletRequest(), response, new AccessDeniedException("x")))),
                Map.entry("internal guard 404", () -> write(response -> {
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x/internal/y");
                    request.setServletPath("/api/x/internal/y");
                    request.addHeader("X-Forwarded-For", "203.0.113.9");
                    new InternalEndpointGuard(new InternalServiceToken("t", false), MAPPER).doFilter(request,
                            response, new MockFilterChain());
                })));
    }

    @Test
    void everyErrorBodyQuotesTheIdOfTheLoggingContext() throws Exception {
        MDC.put(RequestIds.MDC_KEY, ID);
        List<String> checked = new ArrayList<>();
        for (Map.Entry<String, Supplier<String>> writer : writers().entrySet()) {
            JsonNode body = MAPPER.readTree(writer.getValue().get());
            assertThat(body.path("reference").asText()).as(writer.getKey()).isEqualTo(ID);
            assertThat(body.size()).as(writer.getKey()).isEqualTo(6);
            checked.add(writer.getKey());
        }
        assertThat(checked).hasSize(12);
    }

    @Test
    void withoutAnIdTheBodiesKeepTheirFiveFields() throws Exception {
        for (Map.Entry<String, Supplier<String>> writer : writers().entrySet()) {
            JsonNode body = MAPPER.readTree(writer.getValue().get());
            assertThat(body.has("reference")).as(writer.getKey()).isFalse();
            assertThat(body.size()).as(writer.getKey()).isEqualTo(5);
        }
    }

    @Test
    void theReferenceOfA5xxIsTheRequestId() throws Exception {
        MDC.put(RequestIds.MDC_KEY, ID);

        ApiResponse<Void> catchAll = new GlobalExceptionHandler().handleGeneralException(new IllegalStateException())
                .getBody();
        ApiResponse<Void> hidden = new GlobalExceptionHandler(true)
                .handleBusinessException(new BusinessException("secret", "X", 502)).getBody();
        ApiResponse<Void> conflict = new DataAccessExceptionHandler()
                .handleDataIntegrity(new DataIntegrityViolationException("dup")).getBody();

        assertThat(catchAll.getMessage()).isEqualTo("Internal server error (reference " + ID + ")");
        assertThat(hidden.getMessage()).isEqualTo("Internal server error (reference " + ID + ")");
        assertThat(conflict.getMessage()).endsWith("(reference " + ID + ")");

        // Without one: a UUID, as before, and no reference field.
        MDC.clear();
        ApiResponse<Void> plain = new GlobalExceptionHandler().handleGeneralException(new IllegalStateException())
                .getBody();
        assertThat(plain.getMessage()).matches("Internal server error \\(reference [0-9a-f-]{36}\\)");
        assertThat(plain.getReference()).isNull();
    }

    @Test
    void theErrorPageFallsBackToTheIdTheFilterStoredOnTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
        request.setAttribute(RequestIds.REQUEST_ATTRIBUTE, "stored-1");

        ApiResponse<Object> body = new CustomErrorController().handleError(request).getBody();

        assertThat(body.getReference()).isEqualTo("stored-1");
        assertThat(body.getMessage()).isEqualTo("Internal server error");
    }

    @Test
    void successBodiesNeverCarryIt() throws Exception {
        MDC.put(RequestIds.MDC_KEY, ID);

        JsonNode body = MAPPER.readTree(MAPPER.writeValueAsString(ApiResponse.success("x")));

        assertThat(body.has("reference")).isFalse();
    }

    private interface ResponseWriter {
        void write(MockHttpServletResponse response) throws Exception;
    }

    private static String write(ResponseWriter writer) {
        try {
            MockHttpServletResponse response = new MockHttpServletResponse();
            writer.write(response);
            return response.getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String json(ResponseEntity<?> entity) {
        try {
            return MAPPER.writeValueAsString(entity.getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
