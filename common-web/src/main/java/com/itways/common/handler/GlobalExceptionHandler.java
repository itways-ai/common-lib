package com.itways.common.handler;

import com.itways.common.constants.ErrorCodes;
import com.itways.common.exception.BusinessException;
import com.itways.common.response.ApiResponse;
import com.itways.security.core.SecurityMessages;
import com.itways.web.correlation.CurrentRequestId;

import jakarta.validation.ConstraintViolationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The platform's shared error mapping (AS-13), for every service.
 *
 * <p>Previously every framework exception fell into the catch-all as HTTP 500
 * with {@code ex.getMessage()} in the body — raw SQL, constraint names, Java
 * signatures. Now client mistakes get their proper 4xx with a fixed or
 * field-level message, and an unexpected failure returns a fixed text plus a
 * reference id that is logged next to the stack trace. No 5xx built here ever
 * carries an exception message.
 *
 * <p>{@code DataIntegrityViolationException} (409) lives in
 * {@link DataAccessExceptionHandler}, so services without spring-tx never load
 * it.
 *
 * <p>Imported by {@code CommonConfig}; the {@code @Component} name is the one
 * the former component scan gave, so a service that refers to the bean by name
 * still finds it.
 *
 * <p><b>Overridable (ARC-11).</b> A service that needs its own codes or
 * messages no longer copies the class: it registers a subclass
 * ({@code class AccountExceptionHandler extends GlobalExceptionHandler}, a
 * {@code @RestControllerAdvice} of its own) and overrides what differs. Every
 * {@code @ExceptionHandler} method is public and overridable (Spring finds the
 * annotation on the overridden superclass method), and the shared shapes are
 * protected hooks: {@link #error}, {@link #validationFailed},
 * {@link #newReference}, {@link #clientErrorCode} and
 * {@link #hideServerErrorMessages}. The base is
 * {@code @ConditionalOnMissingBean(GlobalExceptionHandler.class)}, so the
 * subclass replaces it instead of running beside it. Defaults are unchanged:
 * a service that overrides nothing gets exactly the answers it got before.
 *
 * <p>{@code itways.errors.hide-server-error-messages} (default {@code false}):
 * when {@code true}, a {@link BusinessException} with a 5xx status answers its
 * own status with the fixed text {@code Internal server error (reference X)}
 * and code {@code INTERNAL_SERVER_ERROR}, and the real message is logged with
 * the reference (the account/auth rule); when {@code false} the thrower's
 * message is returned as it always was.
 *
 * <p><b>Request id (ARC-25).</b> Every error body built here carries the
 * current request id as {@code reference} when there is one
 * ({@link CurrentRequestId}; the field is left out otherwise), and the
 * reference of a 500 is that same id, so the caller, the response header and
 * the log lines of every service the request touched agree.
 */
@Slf4j
@RestControllerAdvice
@Component("globalExceptionHandler")
@ConditionalOnMissingBean(GlobalExceptionHandler.class)
public class GlobalExceptionHandler {

    public static final String HIDE_SERVER_ERROR_MESSAGES_PROPERTY = "itways.errors.hide-server-error-messages";

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String VALIDATION_FAILED_MESSAGE = "Validation Failed";
    public static final String NOT_FOUND_MESSAGE = "The requested resource was not found";
    public static final String NOT_FOUND_CODE = "NOT_FOUND";
    public static final String INTERNAL_ERROR_CODE = "INTERNAL_SERVER_ERROR";

    private final boolean hideServerErrorMessages;

    /** Today's defaults: 5xx business messages are returned as thrown. */
    public GlobalExceptionHandler() {
        this(false);
    }

    @Autowired
    public GlobalExceptionHandler(
            @Value("${" + HIDE_SERVER_ERROR_MESSAGES_PROPERTY + ":false}") boolean hideServerErrorMessages) {
        this.hideServerErrorMessages = hideServerErrorMessages;
    }

    /**
     * The message and code are the thrower's deliberate, client-facing text,
     * whatever the status — unless {@link #hideServerErrorMessages()} and the
     * status is 5xx: then a fixed text with a reference, and the real message
     * goes to the log.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        if (ex.getHttpStatus() >= 500) {
            if (hideServerErrorMessages()) {
                String reference = newReference();
                log.error("Business exception with status {} ({}) (reference {}): {}", ex.getHttpStatus(),
                        ex.getErrorCode(), reference, ex.getMessage(), ex);
                return error(HttpStatusCode.valueOf(ex.getHttpStatus()),
                        "Internal server error (reference " + reference + ")", INTERNAL_ERROR_CODE);
            }
            log.warn("Business exception with status {} ({}): {}", ex.getHttpStatus(), ex.getErrorCode(),
                    ex.getMessage());
        }
        return ResponseEntity.status(ex.getHttpStatus())
                .body(CurrentRequestId.stamp(ApiResponse.error(ex.getMessage(), ex.getErrorCode())));
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    /** Field → message in {@code data}, as before. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            // Class-level constraints produce an ObjectError; the old cast to
            // FieldError turned those into a 500.
            String key = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            errors.putIfAbsent(key, error.getDefaultMessage());
        });
        return validationFailed(errors);
    }

    /** Constraints on {@code @RequestParam} / {@code @PathVariable} (Spring 6.1 method validation). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodValidation(
            HandlerMethodValidationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getAllValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            String parameter = name != null ? name : "arg" + result.getMethodParameter().getParameterIndex();
            result.getResolvableErrors().forEach(error -> {
                String key = error instanceof FieldError fieldError
                        ? parameter + "." + fieldError.getField()
                        : parameter;
                errors.putIfAbsent(key, error.getDefaultMessage());
            });
        });
        return validationFailed(errors);
    }

    /** {@code @Validated} beans and services. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (ex.getConstraintViolations() != null) {
            ex.getConstraintViolations().forEach(violation -> errors.putIfAbsent(
                    String.valueOf(violation.getPropertyPath()), violation.getMessage()));
        }
        return validationFailed(errors);
    }

    // ── Malformed requests ─────────────────────────────────────────────────────

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON", "MALFORMED_REQUEST");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "Required parameter '" + ex.getParameterName() + "' is missing",
                "MISSING_PARAMETER");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
        return error(HttpStatus.BAD_REQUEST, "Required part '" + ex.getRequestPartName() + "' is missing",
                "MISSING_PARAMETER");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Parameter '" + ex.getName() + "' has an invalid value",
                "INVALID_PARAMETER");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type is not supported by this endpoint",
                "UNSUPPORTED_MEDIA_TYPE");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(headers)
                .body(CurrentRequestId.stamp(ApiResponse.error(
                        "Method " + ex.getMethod() + " is not supported on this path", "METHOD_NOT_ALLOWED")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE, NOT_FOUND_CODE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "The request is too large", "PAYLOAD_TOO_LARGE");
    }

    /** Any other multipart parsing failure (not multipart, truncated body, bad boundary). */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        log.debug("Unreadable multipart request: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "The multipart request could not be read", "MALFORMED_REQUEST");
    }

    // ── Security ───────────────────────────────────────────────────────────────

    /** Method security ({@code @PreAuthorize}) denials: were a 500 with the message. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, SecurityMessages.PERMISSION_DENIED, ErrorCodes.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, SecurityMessages.AUTHENTICATION_REQUIRED, ErrorCodes.UNAUTHORIZED);
    }

    // ── Fallback ───────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        // A framework exception that already knows it is a client error (a
        // missing header, an unacceptable Accept, ...) keeps its 4xx.
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = errorResponse.getStatusCode();
            HttpStatus known = HttpStatus.resolve(status.value());
            String message = known != null ? known.getReasonPhrase() : "Bad request";
            // A ResponseStatusException's reason is the thrower's own client-facing text.
            if (ex instanceof ResponseStatusException rse && rse.getReason() != null) {
                message = rse.getReason();
            }
            return error(status, message, clientErrorCode(status));
        }
        String reference = newReference();
        log.error("Unhandled exception (reference {})", reference, ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error (reference " + reference + ")",
                INTERNAL_ERROR_CODE);
    }

    // ── Hooks for subclasses ───────────────────────────────────────────────────

    /** The 400 {@code VALIDATION_ERROR} answer: field → message in {@code data}; the request id as {@code reference}. */
    protected ResponseEntity<ApiResponse<Map<String, String>>> validationFailed(Map<String, String> errors) {
        ApiResponse<Map<String, String>> response = ApiResponse.success(VALIDATION_FAILED_MESSAGE, errors);
        response.setStatus("error");
        response.setErrorCode(VALIDATION_ERROR);
        return ResponseEntity.badRequest().body(CurrentRequestId.stamp(response));
    }

    /** An error answer in the envelope, with the request id as {@code reference}; every fixed-text handler ends here. */
    protected ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message, String errorCode) {
        return error((HttpStatusCode) status, message, errorCode);
    }

    /** As {@link #error(HttpStatus, String, String)}, for a status outside the {@link HttpStatus} enum. */
    protected ResponseEntity<ApiResponse<Void>> error(HttpStatusCode status, String message, String errorCode) {
        return ResponseEntity.status(status).body(CurrentRequestId.stamp(ApiResponse.error(message, errorCode)));
    }

    /**
     * The reference a caller can quote from a 5xx body; the log carries the same
     * one. The current request id when there is one (ARC-25), a UUID otherwise.
     */
    protected String newReference() {
        return CurrentRequestId.get().orElseGet(() -> UUID.randomUUID().toString());
    }

    /**
     * The error code of a framework 4xx the catch-all lets through:
     * {@code HTTP_<status>}. auth-service answers {@link HttpStatus#name()}.
     */
    protected String clientErrorCode(HttpStatusCode status) {
        return "HTTP_" + status.value();
    }

    /**
     * Whether a 5xx {@link BusinessException} hides its message behind a
     * reference; {@code itways.errors.hide-server-error-messages} by default.
     */
    protected boolean hideServerErrorMessages() {
        return hideServerErrorMessages;
    }
}
