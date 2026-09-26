package com.itways.common.handler;

import com.itways.common.constants.ErrorCodes;
import com.itways.common.exception.BusinessException;
import com.itways.common.response.ApiResponse;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
 * The platform's shared error mapping (AS-13), for every service that has no
 * more specific advice of its own (account-service and auth-service run theirs
 * ahead of this one).
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
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    static final String NOT_FOUND_MESSAGE = "The requested resource was not found";
    static final String NOT_FOUND_CODE = "NOT_FOUND";
    static final String INTERNAL_ERROR_CODE = "INTERNAL_SERVER_ERROR";

    /**
     * Unchanged: the message and code are the thrower's deliberate, client-facing
     * text, whatever the status.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        if (ex.getHttpStatus() >= 500) {
            log.warn("Business exception with status {} ({}): {}", ex.getHttpStatus(), ex.getErrorCode(),
                    ex.getMessage());
        }
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
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
                .body(ApiResponse.error("Method " + ex.getMethod() + " is not supported on this path",
                        "METHOD_NOT_ALLOWED"));
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
        return error(HttpStatus.FORBIDDEN, "Access denied", ErrorCodes.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Authentication required", ErrorCodes.UNAUTHORIZED);
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
            return ResponseEntity.status(status).body(ApiResponse.error(message, "HTTP_" + status.value()));
        }
        String reference = UUID.randomUUID().toString();
        log.error("Unhandled exception (reference {})", reference, ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error (reference " + reference + ")",
                INTERNAL_ERROR_CODE);
    }

    private static ResponseEntity<ApiResponse<Map<String, String>>> validationFailed(Map<String, String> errors) {
        ApiResponse<Map<String, String>> response = ApiResponse.success("Validation Failed", errors);
        response.setStatus("error");
        response.setErrorCode(VALIDATION_ERROR);
        return ResponseEntity.badRequest().body(response);
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message, String errorCode) {
        return ResponseEntity.status(status).body(ApiResponse.error(message, errorCode));
    }
}
