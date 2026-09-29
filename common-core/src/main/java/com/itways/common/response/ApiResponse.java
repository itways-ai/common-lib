package com.itways.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private LocalDateTime timestamp;
    private String status;
    private String message;
    private T data;
    private String errorCode;

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
