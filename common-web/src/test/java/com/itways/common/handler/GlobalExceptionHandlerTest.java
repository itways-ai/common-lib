package com.itways.common.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.itways.annotation.EnableCommon;
import com.itways.common.exception.BusinessException;
import com.itways.common.response.ApiResponse;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

/**
 * Every handler called directly (no MockMvc), the hooks a subclass overrides,
 * the hide flag, and that a service's subclass replaces the base (ARC-11).
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unused")
    void sample(String value) {
    }

    private static MethodParameter parameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class);
        return new MethodParameter(method, 0);
    }

    // ── BusinessException and the hide flag ────────────────────────────────────

    @Test
    void aBusinessExceptionKeepsItsMessageAndCodeByDefault() {
        ResponseEntity<ApiResponse<Void>> client = handler.handleBusinessException(
                new BusinessException("Journey is inactive", "JOURNEY_INACTIVE", 422));
        assertThat(client.getStatusCode().value()).isEqualTo(422);
        assertThat(client.getBody().getMessage()).isEqualTo("Journey is inactive");
        assertThat(client.getBody().getErrorCode()).isEqualTo("JOURNEY_INACTIVE");

        // Today's default: a 5xx business message is the thrower's client-facing text.
        ResponseEntity<ApiResponse<Void>> server = handler.handleBusinessException(
                new BusinessException("Could not check the journey right now; try again", "EXTERNAL_SERVICE_ERROR",
                        503));
        assertThat(server.getStatusCode().value()).isEqualTo(503);
        assertThat(server.getBody().getMessage()).isEqualTo("Could not check the journey right now; try again");
        assertThat(server.getBody().getErrorCode()).isEqualTo("EXTERNAL_SERVICE_ERROR");
        assertThat(handler.hideServerErrorMessages()).isFalse();
    }

    @Test
    void theHideFlagMasksA5xxBusinessMessageBehindAReference(CapturedOutput output) {
        GlobalExceptionHandler hiding = new GlobalExceptionHandler(true);

        ResponseEntity<ApiResponse<Void>> server = hiding.handleBusinessException(
                new BusinessException("db down at pg://internal-host", "EXTERNAL_SERVICE_ERROR", 503));

        // Its own status, the fixed text with a reference, the internal code; the real message in the log.
        assertThat(server.getStatusCode().value()).isEqualTo(503);
        assertThat(server.getBody().getMessage()).matches("Internal server error \\(reference [0-9a-f-]{36}\\)");
        assertThat(server.getBody().getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        String reference = server.getBody().getMessage().replaceAll(".*reference (.*)\\)", "$1");
        assertThat(output.getAll()).contains("db down at pg://internal-host").contains(reference);

        // 4xx is untouched.
        ResponseEntity<ApiResponse<Void>> client = hiding.handleBusinessException(
                new BusinessException("Not yours", "FORBIDDEN", 403));
        assertThat(client.getBody().getMessage()).isEqualTo("Not yours");
    }

    @Test
    void theHideFlagIsBoundFromTheProperty() {
        new WebApplicationContextRunner().withUserConfiguration(App.class)
                .withPropertyValues("itways.errors.hide-server-error-messages=true").run(context -> {
                    GlobalExceptionHandler bean = context.getBean(GlobalExceptionHandler.class);
                    assertThat(bean.hideServerErrorMessages()).isTrue();
                    assertThat(bean.handleBusinessException(new BusinessException("secret", "X", 500)).getBody()
                            .getMessage()).startsWith("Internal server error (reference ");
                });
        new WebApplicationContextRunner().withUserConfiguration(App.class).run(context -> assertThat(
                context.getBean(GlobalExceptionHandler.class).hideServerErrorMessages()).isFalse());
    }

    // ── Hooks ──────────────────────────────────────────────────────────────────

    @Test
    void aSubclassOverridesTheClientErrorCode() {
        GlobalExceptionHandler authStyle = new GlobalExceptionHandler() {
            @Override
            protected String clientErrorCode(HttpStatusCode status) {
                HttpStatus known = HttpStatus.resolve(status.value());
                return known != null ? known.name() : "BAD_REQUEST";
            }
        };
        HttpMediaTypeNotAcceptableException notAcceptable = new HttpMediaTypeNotAcceptableException("no");

        assertThat(handler.handleGeneralException(notAcceptable).getBody().getErrorCode()).isEqualTo("HTTP_406");
        assertThat(authStyle.handleGeneralException(notAcceptable).getBody().getErrorCode())
                .isEqualTo("NOT_ACCEPTABLE");
        assertThat(authStyle.handleGeneralException(notAcceptable).getStatusCode().value()).isEqualTo(406);
    }

    @Test
    void aSubclassOverridesTheReferenceAndTheErrorShape() {
        GlobalExceptionHandler custom = new GlobalExceptionHandler() {
            @Override
            protected String newReference() {
                return "ref-1";
            }

            @Override
            protected ResponseEntity<ApiResponse<Void>> error(HttpStatusCode status, String message,
                    String errorCode) {
                return ResponseEntity.status(status).header("X-Handled", "yes")
                        .body(ApiResponse.error(message, errorCode));
            }
        };

        ResponseEntity<ApiResponse<Void>> answer = custom.handleGeneralException(new IllegalStateException("boom"));
        assertThat(answer.getBody().getMessage()).isEqualTo("Internal server error (reference ref-1)");
        assertThat(answer.getHeaders().getFirst("X-Handled")).isEqualTo("yes");
        // Every fixed-text handler goes through error(...).
        assertThat(custom.handleMultipart(new MultipartException("x")).getHeaders().getFirst("X-Handled"))
                .isEqualTo("yes");
    }

    // ── Validation shapes ──────────────────────────────────────────────────────

    @Test
    void validationErrorsMapFieldsAndObjects() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.rejectValue(null, "x", "invalid pair");
        binding.addError(new FieldError("request", "name", "must not be blank"));
        binding.addError(new FieldError("request", "name", "second message for the same field"));

        ResponseEntity<ApiResponse<Map<String, String>>> answer = handler.handleValidationExceptions(
                new MethodArgumentNotValidException(parameter(), binding));

        assertThat(answer.getStatusCode().value()).isEqualTo(400);
        assertThat(answer.getBody().getStatus()).isEqualTo("error");
        assertThat(answer.getBody().getMessage()).isEqualTo("Validation Failed");
        assertThat(answer.getBody().getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(answer.getBody().getData()).containsExactly(Map.entry("request", "invalid pair"),
                Map.entry("name", "must not be blank"));
    }

    @Test
    void methodValidationNamesTheParameterOrFallsBackToItsIndex() throws Exception {
        MethodParameter unnamed = parameter();
        ParameterValidationResult plain = new ParameterValidationResult(unnamed, "x",
                List.of(new DefaultMessageSourceResolvable(new String[] { "Size" }, "too short")));
        ParameterValidationResult nested = new ParameterValidationResult(unnamed, "x",
                List.of(new FieldError("arg", "email", "must be an email")));
        HandlerMethodValidationException ex = new HandlerMethodValidationException(
                MethodValidationResult.create(this, unnamed.getMethod(), List.of(plain, nested)));

        assertThat(handler.handleMethodValidation(ex).getBody().getData())
                .containsExactly(Map.entry("arg0", "too short"), Map.entry("arg0.email", "must be an email"));

        MethodParameter named = parameter();
        named.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        if (named.getParameterName() != null) { // compiled with -parameters (the Boot parent does)
            HandlerMethodValidationException namedEx = new HandlerMethodValidationException(
                    MethodValidationResult.create(this, named.getMethod(),
                            List.of(new ParameterValidationResult(named, "x", plain.getResolvableErrors()))));
            assertThat(handler.handleMethodValidation(namedEx).getBody().getData())
                    .containsExactly(Map.entry("value", "too short"));
        }
    }

    @Test
    void constraintViolationsKeepTheFullPathAndAreNullSafe() {
        assertThat(handler.handleConstraintViolation(new ConstraintViolationException(null)).getBody().getData())
                .isEmpty();
        assertThat(handler.handleConstraintViolation(new ConstraintViolationException(null)).getStatusCode().value())
                .isEqualTo(400);

        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("update.request.email");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be an email");

        assertThat(handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation))).getBody()
                .getData()).containsExactly(Map.entry("update.request.email", "must be an email"));
    }

    // ── Malformed requests ─────────────────────────────────────────────────────

    @Test
    void malformedRequestsGetTheirFixedCodes() throws Exception {
        ResponseEntity<ApiResponse<Void>> unreadable = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0])));
        assertThat(unreadable.getStatusCode().value()).isEqualTo(400);
        assertThat(unreadable.getBody().getMessage()).isEqualTo("Request body is missing or is not valid JSON");
        assertThat(unreadable.getBody().getErrorCode()).isEqualTo("MALFORMED_REQUEST");

        ResponseEntity<ApiResponse<Void>> missing = handler.handleMissingParameter(
                new MissingServletRequestParameterException("page", "int"));
        assertThat(missing.getStatusCode().value()).isEqualTo(400);
        assertThat(missing.getBody().getMessage()).isEqualTo("Required parameter 'page' is missing");
        assertThat(missing.getBody().getErrorCode()).isEqualTo("MISSING_PARAMETER");

        ResponseEntity<ApiResponse<Void>> part = handler.handleMissingPart(new MissingServletRequestPartException("file"));
        assertThat(part.getBody().getMessage()).isEqualTo("Required part 'file' is missing");
        assertThat(part.getBody().getErrorCode()).isEqualTo("MISSING_PARAMETER");

        ResponseEntity<ApiResponse<Void>> mismatch = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Integer.class, "page", parameter(), null));
        assertThat(mismatch.getBody().getMessage()).isEqualTo("Parameter 'page' has an invalid value");
        assertThat(mismatch.getBody().getErrorCode()).isEqualTo("INVALID_PARAMETER");

        ResponseEntity<ApiResponse<Void>> media = handler.handleMediaType(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));
        assertThat(media.getStatusCode().value()).isEqualTo(415);
        assertThat(media.getBody().getErrorCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");

        ResponseEntity<ApiResponse<Void>> method = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "POST")));
        assertThat(method.getStatusCode().value()).isEqualTo(405);
        assertThat(method.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
        assertThat(method.getBody().getMessage()).isEqualTo("Method PATCH is not supported on this path");
        assertThat(method.getBody().getErrorCode()).isEqualTo("METHOD_NOT_ALLOWED");

        ResponseEntity<ApiResponse<Void>> notFound = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "/nowhere"));
        assertThat(notFound.getStatusCode().value()).isEqualTo(404);
        assertThat(notFound.getBody().getMessage()).isEqualTo("The requested resource was not found");
        assertThat(notFound.getBody().getErrorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void multipartFailuresAre400And413() {
        ResponseEntity<ApiResponse<Void>> multipart = handler.handleMultipart(new MultipartException("truncated"));
        assertThat(multipart.getStatusCode().value()).isEqualTo(400);
        assertThat(multipart.getBody().getMessage()).isEqualTo("The multipart request could not be read");
        assertThat(multipart.getBody().getErrorCode()).isEqualTo("MALFORMED_REQUEST");

        ResponseEntity<ApiResponse<Void>> tooLarge = handler.handleUploadTooLarge(
                new MaxUploadSizeExceededException(1024));
        assertThat(tooLarge.getStatusCode().value()).isEqualTo(413);
        assertThat(tooLarge.getBody().getMessage()).isEqualTo("The request is too large");
        assertThat(tooLarge.getBody().getErrorCode()).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    // ── Security ───────────────────────────────────────────────────────────────

    @Test
    void authenticationFailuresAre401AndDenialsAre403() {
        ResponseEntity<ApiResponse<Void>> unauthorized = handler.handleAuthentication(
                new InsufficientAuthenticationException("anonymous"));
        assertThat(unauthorized.getStatusCode().value()).isEqualTo(401);
        assertThat(unauthorized.getBody().getMessage()).isEqualTo("Authentication is required");
        assertThat(unauthorized.getBody().getErrorCode()).isEqualTo("AUTH_401");

        ResponseEntity<ApiResponse<Void>> forbidden = handler.handleAccessDenied(new AccessDeniedException("no"));
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(forbidden.getBody().getMessage()).isEqualTo("You do not have permission to perform this action");
        assertThat(forbidden.getBody().getErrorCode()).isEqualTo("AUTH_403");
    }

    // ── Fallback ───────────────────────────────────────────────────────────────

    @Test
    void frameworkClientErrorsKeepTheirStatusAndAResponseStatusExceptionItsReason() throws Exception {
        ResponseEntity<ApiResponse<Void>> withReason = handler.handleGeneralException(
                new ResponseStatusException(HttpStatus.CONFLICT, "Already there"));
        assertThat(withReason.getStatusCode().value()).isEqualTo(409);
        assertThat(withReason.getBody().getMessage()).isEqualTo("Already there");
        assertThat(withReason.getBody().getErrorCode()).isEqualTo("HTTP_409");

        ResponseEntity<ApiResponse<Void>> withoutReason = handler.handleGeneralException(
                new ResponseStatusException(HttpStatus.GONE));
        assertThat(withoutReason.getStatusCode().value()).isEqualTo(410);
        assertThat(withoutReason.getBody().getMessage()).isEqualTo("Gone");
        assertThat(withoutReason.getBody().getErrorCode()).isEqualTo("HTTP_410");

        ResponseEntity<ApiResponse<Void>> header = handler.handleGeneralException(
                new MissingRequestHeaderException("X-Tenant", parameter()));
        assertThat(header.getStatusCode().value()).isEqualTo(400);
        assertThat(header.getBody().getMessage()).isEqualTo("Bad Request");
        assertThat(header.getBody().getErrorCode()).isEqualTo("HTTP_400");

        // A 5xx ErrorResponse is not a client error: the reference path.
        ResponseEntity<ApiResponse<Void>> server = handler.handleGeneralException(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream said no"));
        assertThat(server.getStatusCode().value()).isEqualTo(500);
        assertThat(server.getBody().getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    void anythingElseIsA500WithAReferenceAndNoMessage(CapturedOutput output) {
        ResponseEntity<ApiResponse<Void>> answer = handler.handleGeneralException(
                new IllegalStateException("boom: jdbc://db/secret"));

        assertThat(answer.getStatusCode().value()).isEqualTo(500);
        assertThat(answer.getBody().getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(answer.getBody().getMessage()).matches("Internal server error \\(reference [0-9a-f-]{36}\\)")
                .doesNotContain("secret");
        String reference = answer.getBody().getMessage().replaceAll(".*reference (.*)\\)", "$1");
        assertThat(output.getAll()).contains("Unhandled exception (reference " + reference + ")")
                .contains("jdbc://db/secret");
    }

    // ── A service's subclass replaces the base ─────────────────────────────────

    @Configuration(proxyBeanMethods = false)
    @EnableCommon
    static class App {
    }

    /** What account-service would register: a scanned {@code @RestControllerAdvice} extending the base. */
    @RestControllerAdvice
    static class OwnAdvice extends GlobalExceptionHandler {

        @Override
        protected boolean hideServerErrorMessages() {
            return true;
        }
    }

    @Test
    void withoutASubclassTheBaseIsTheOnlyAdvice() {
        new WebApplicationContextRunner().withUserConfiguration(App.class).run(context -> {
            assertThat(context).hasNotFailed().hasBean("globalExceptionHandler")
                    .hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context.getBean(GlobalExceptionHandler.class).getClass())
                    .isEqualTo(GlobalExceptionHandler.class);
        });
    }

    @Test
    void aServiceSubclassReplacesTheBase() {
        new WebApplicationContextRunner().withUserConfiguration(OwnAdvice.class, App.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(GlobalExceptionHandler.class)
                    .doesNotHaveBean("globalExceptionHandler");
            GlobalExceptionHandler bean = context.getBean(GlobalExceptionHandler.class);
            assertThat(bean).isInstanceOf(OwnAdvice.class);
            assertThat(bean.hideServerErrorMessages()).isTrue();
            // The rest of @EnableCommon is untouched.
            assertThat(context).hasBean("dataAccessExceptionHandler").hasBean("customErrorController");
        });
    }
}
