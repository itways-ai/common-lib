package com.itways.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiResponse is the envelope every Nibras service returns and every client
 * parses; the three static factories define the status/errorCode conventions
 * ("success" with a null errorCode, "error" with a null data) that client-side
 * success checks key on. Nothing here is clever — it is pinned because every
 * consumer asserts it implicitly. (PageResponse is a pure data holder with no
 * logic, so it gets no test of its own.)
 */
@DisplayName("ApiResponse")
class ApiResponseTest {

    @Test
    @DisplayName("success(data) stamps status 'success', the stock message, and no errorCode")
    void successWithData() {
        ApiResponse<String> response = ApiResponse.success("payload");

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Operation completed successfully");
        assertThat(response.getData()).isEqualTo("payload");
        assertThat(response.getErrorCode()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("success(message, data) keeps the 'success' status with a custom message")
    void successWithMessage() {
        ApiResponse<String> response = ApiResponse.success("Journey published", "payload");

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Journey published");
        assertThat(response.getData()).isEqualTo("payload");
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    @DisplayName("error(message, errorCode) stamps status 'error', the code, and null data")
    void errorFactory() {
        ApiResponse<Object> response = ApiResponse.error("Journey not found", "RES_001");

        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getMessage()).isEqualTo("Journey not found");
        assertThat(response.getErrorCode()).isEqualTo("RES_001");
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }
}
