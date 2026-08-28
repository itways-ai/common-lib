package com.itways.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BusinessException carries the HTTP status and error code that
 * GlobalExceptionHandler copies verbatim into the response, so the defaults
 * each constructor fills in ARE the API behavior for every service that throws
 * one. The three constructors' differing defaults are pinned here.
 */
@DisplayName("BusinessException")
class BusinessExceptionTest {

    @Test
    @DisplayName("the full constructor keeps message, code, and status exactly as given")
    void fullConstructor() {
        BusinessException ex = new BusinessException("Journey not found", "RES_001", 404);

        assertThat(ex.getMessage()).isEqualTo("Journey not found");
        assertThat(ex.getErrorCode()).isEqualTo("RES_001");
        assertThat(ex.getHttpStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("omitting the status defaults it to 500 while keeping the given code")
    void messageAndCodeConstructor() {
        // Callers using this constructor for validation-ish errors get a 500,
        // not a 4xx — worth knowing before reaching for it.
        BusinessException ex = new BusinessException("quota exceeded", "QUOTA_001");

        assertThat(ex.getErrorCode()).isEqualTo("QUOTA_001");
        assertThat(ex.getHttpStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("the message-only constructor defaults to BUSINESS_ERROR and 500")
    void messageOnlyConstructor() {
        BusinessException ex = new BusinessException("something went wrong");

        assertThat(ex.getMessage()).isEqualTo("something went wrong");
        assertThat(ex.getErrorCode()).isEqualTo("BUSINESS_ERROR");
        assertThat(ex.getHttpStatus()).isEqualTo(500);
    }
}
