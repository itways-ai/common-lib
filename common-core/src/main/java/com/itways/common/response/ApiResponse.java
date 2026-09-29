package com.itways.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The platform's response envelope.
 *
 * <p>
 * {@code reference} (ARC-25) is the request id an error answer quotes, so a
 * caller can hand it to support and the logs of every service the request
 * touched can be searched for it. It is left out of the JSON when not set:
 * success answers, and error answers written outside a request with an id,
 * keep exactly the five fields they always had.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private LocalDateTime timestamp;
    private String status;
    private String message;
    private T data;
    private String errorCode;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String reference;

    /** The five-field envelope, without a reference (the constructor from before ARC-25). */
    public ApiResponse(LocalDateTime timestamp, String status, String message, T data, String errorCode) {
        this(timestamp, status, message, data, errorCode, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                LocalDateTime.now(),
                "success",
                "Operation completed successfully",
                data,
                null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(
                LocalDateTime.now(),
                "success",
                message,
                data,
                null);
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return new ApiResponse<>(
                LocalDateTime.now(),
                "error",
                message,
                null,
                errorCode);
    }
}
