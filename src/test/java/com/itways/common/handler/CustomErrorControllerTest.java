package com.itways.common.handler;

import com.itways.common.response.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CustomErrorController catches everything the MVC dispatcher never saw —
 * unmatched paths and exceptions thrown in filters — and squeezes it into the
 * same ApiResponse envelope as the rest of the platform. Pinned here: how the
 * jakarta.servlet.error.* attributes resolve to a status, and which of the
 * three message sources (exception, message attribute, canned default) wins.
 */
@DisplayName("CustomErrorController")
class CustomErrorControllerTest {

    private final CustomErrorController controller = new CustomErrorController();

    private static MockHttpServletRequest errorRequest(Object status, Object message, Object exception) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (status != null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        }
        if (message != null) {
            request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
        }
        if (exception != null) {
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, exception);
        }
        return request;
    }

    private ApiResponse<Object> body(ResponseEntity<ApiResponse<Object>> response) {
        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    @Nested
    @DisplayName("status resolution")
    class StatusResolution {

        @Test
        @DisplayName("the container's status attribute drives both the HTTP status and the errorCode suffix")
        void statusAttributeDrivesStatus() {
            ResponseEntity<ApiResponse<Object>> response =
                    controller.handleError(errorRequest(404, null, null));

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(body(response).getErrorCode()).isEqualTo("FRAMEWORK_ERROR_404");
            assertThat(body(response).getStatus()).isEqualTo("error");
        }

        @Test
        @DisplayName("no status attribute at all defaults to 500")
        void missingStatusDefaultsTo500() {
            ResponseEntity<ApiResponse<Object>> response =
                    controller.handleError(errorRequest(null, null, null));

            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(body(response).getErrorCode()).isEqualTo("FRAMEWORK_ERROR_500");
            assertThat(body(response).getMessage())
                    .isEqualTo("An unexpected error occurred at the framework level");
        }

        @Test
        @DisplayName("a status code HttpStatus cannot resolve collapses to 500")
        void unresolvableStatusCollapsesTo500() {
            // Containers and proxies emit non-standard codes (599 et al.);
            // HttpStatus.resolve returns null for them and the controller must
            // not NPE building the response.
            ResponseEntity<ApiResponse<Object>> response =
                    controller.handleError(errorRequest(599, null, null));

            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(body(response).getErrorCode()).isEqualTo("FRAMEWORK_ERROR_500");
        }
    }

    @Nested
    @DisplayName("message precedence")
    class MessagePrecedence {

        @Test
        @DisplayName("an exception attribute outranks the container's message attribute")
        void exceptionOutranksMessageAttribute() {
            ResponseEntity<ApiResponse<Object>> response = controller.handleError(errorRequest(
                    500, "container-provided message", new IllegalStateException("filter blew up")));

            assertThat(body(response).getMessage()).isEqualTo("filter blew up");
        }

        @Test
        @DisplayName("without an exception, the container's message attribute is used")
        void messageAttributeUsed() {
            ResponseEntity<ApiResponse<Object>> response =
                    controller.handleError(errorRequest(400, "Required header X-API-KEY missing", null));

            assertThat(body(response).getMessage()).isEqualTo("Required header X-API-KEY missing");
        }

        @Test
        @DisplayName("a bare 404 gets the canned not-found message")
        void notFoundDefault() {
            ResponseEntity<ApiResponse<Object>> response =
                    controller.handleError(errorRequest(404, null, null));

            assertThat(body(response).getMessage()).isEqualTo("The requested resource was not found");
        }

        @Test
        @DisplayName("for 404s the canned message overrides even a container-provided message")
        void notFoundOverridesMessageAttribute() {
            // NOTE: possible defect — the branch order puts the 404 default
            // AFTER the message-attribute read, so a specific message set by
            // the container (or an upstream filter) for a 404 is discarded.
            // Harmless today, surprising the day someone sets one on purpose.
            ResponseEntity<ApiResponse<Object>> response =
                    controller.handleError(errorRequest(404, "No mapping for GET /journeys/42", null));

            assertThat(body(response).getMessage()).isEqualTo("The requested resource was not found");
        }
    }
}
