package com.itways.common.handler;

import com.itways.common.constants.ErrorCodes;
import com.itways.common.exception.BusinessException;
import com.itways.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GlobalExceptionHandler is the single translation layer between exceptions and
 * the ApiResponse envelope every service client parses. The mappings pinned
 * here — BusinessException's embedded status/code, the validation field-error
 * map, and the catch-all 500 — are cross-service API contract: change one and
 * every consumer's error handling changes with it.
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** Stand-in controller signature so a real MethodParameter can be built. */
    @SuppressWarnings("unused")
    private static void validatedEndpoint(String requestBody) {
    }

    private static MethodArgumentNotValidException validationFailure(BindingResult binding) throws Exception {
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("validatedEndpoint", String.class), 0);
        return new MethodArgumentNotValidException(parameter, binding);
    }

    @Nested
    @DisplayName("BusinessException mapping")
    class BusinessExceptions {

        @Test
        @DisplayName("the response carries the exception's embedded httpStatus and errorCode")
        void embeddedStatusAndCode() {
            BusinessException ex = new BusinessException(
                    "Journey not found", ErrorCodes.RESOURCE_NOT_FOUND, 404);

            ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getStatus()).isEqualTo("error");
            assertThat(body.getErrorCode()).isEqualTo("RES_001");
            assertThat(body.getMessage()).isEqualTo("Journey not found");
            assertThat(body.getData()).isNull();
        }

        @Test
        @DisplayName("a BusinessException without an explicit status surfaces as 500")
        void defaultedStatus() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(
                    new BusinessException("payment gateway rejected", "PAY_001"));

            assertThat(response.getStatusCode().value()).isEqualTo(500);
        }
    }

    @Nested
    @DisplayName("validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("field errors become a field-to-message map under a 400 VALIDATION_ERROR envelope")
        void fieldErrorMap() throws Exception {
            BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
            binding.addError(new FieldError("request", "email", "must be a well-formed email address"));
            binding.addError(new FieldError("request", "name", "must not be blank"));

            ResponseEntity<ApiResponse<Map<String, String>>> response =
                    handler.handleValidationExceptions(validationFailure(binding));

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            ApiResponse<Map<String, String>> body = response.getBody();
            assertThat(body).isNotNull();
            // The envelope is built via ApiResponse.success and then mutated,
            // so status/errorCode — not the factory used — are the contract.
            assertThat(body.getStatus()).isEqualTo("error");
            assertThat(body.getErrorCode()).isEqualTo("VALIDATION_ERROR");
            assertThat(body.getMessage()).isEqualTo("Validation Failed");
            assertThat(body.getData())
                    .containsEntry("email", "must be a well-formed email address")
                    .containsEntry("name", "must not be blank")
                    .hasSize(2);
        }

        @Test
        @DisplayName("a class-level (global) validation error escapes the handler entirely")
        void globalErrorBlowsUp() throws Exception {
            // NOTE: possible defect — the handler casts every ObjectError to
            // FieldError, so any cross-field/class-level constraint violation
            // (registered via reject(), not rejectValue()) throws
            // ClassCastException out of the handler and the client gets a bare
            // 500 instead of a validation payload. Pinned as current behavior.
            BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
            binding.reject("dates.order", "start date must precede end date");

            MethodArgumentNotValidException ex = validationFailure(binding);

            assertThatThrownBy(() -> handler.handleValidationExceptions(ex))
                    .isInstanceOf(ClassCastException.class);
        }
    }

    @Nested
    @DisplayName("catch-all handler")
    class CatchAll {

        @Test
        @DisplayName("any unhandled exception becomes a 500 INTERNAL_SERVER_ERROR envelope")
        void mapsTo500() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneralException(new IllegalStateException("boom"));

            assertThat(response.getStatusCode().value()).isEqualTo(500);
            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getStatus()).isEqualTo("error");
            assertThat(body.getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        }

        @Test
        @DisplayName("the raw exception message is concatenated into the client-facing message")
        void rawMessageLeaks() {
            // NOTE: possible defect — raw exception text (SQL fragments, crypto
            // errors, file paths) leaks to clients verbatim. A driver error like
            // the one below hands an attacker schema details on a 500.
            ResponseEntity<ApiResponse<Void>> response = handler.handleGeneralException(
                    new IllegalStateException("ORA-00942: table or view \"JOURNEY_STEPS\" does not exist"));

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getMessage()).isEqualTo(
                    "Internal Server Error: ORA-00942: table or view \"JOURNEY_STEPS\" does not exist");
        }

        @Test
        @DisplayName("an exception without a message yields the literal string 'null' in the envelope")
        void nullMessageBecomesLiteralNull() {
            // Same concatenation, degenerate case: clients see
            // "Internal Server Error: null". Pinned so a cleanup is deliberate.
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneralException(new IllegalStateException());

            ApiResponse<Void> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getMessage()).isEqualTo("Internal Server Error: null");
        }
    }
}
