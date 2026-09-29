package com.itways.common.handler;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.itways.common.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Maps {@link DataIntegrityViolationException} — usually a unique constraint
 * lost to a concurrent request — to 409 with a generic message (AS-13). The
 * driver's message names tables and constraints, so it only goes to the log,
 * under a reference the caller can quote.
 *
 * <p>A class of its own, not a method on {@link GlobalExceptionHandler}: the
 * {@code @ConditionalOnClass} (evaluated from bytecode metadata, before the
 * class is loaded) keeps services without spring-tx, such as
 * notification-service, from ever resolving the exception type and failing to
 * start.
 *
 * <p>Ordered ahead of {@link GlobalExceptionHandler}, whose {@code Exception}
 * catch-all would otherwise claim this exception first. It sits one step below
 * {@link Ordered#HIGHEST_PRECEDENCE} so a service's own highest-precedence
 * advice (account-service, auth-service) still wins deterministically and keeps
 * its codes and messages, instead of depending on bean-registration order.
 */
@Slf4j
@RestControllerAdvice
@Component("dataAccessExceptionHandler")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnClass(name = "org.springframework.dao.DataIntegrityViolationException")
public class DataAccessExceptionHandler {

    static final String DATA_CONFLICT = "DATA_CONFLICT";

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String reference = UUID.randomUUID().toString();
        Throwable cause = ex.getMostSpecificCause();
        log.warn("Data integrity violation (reference {}): {}: {}", reference, cause.getClass().getSimpleName(),
                cause.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                "The request conflicts with existing data; reload and try again (reference " + reference + ")",
                DATA_CONFLICT));
    }
}
