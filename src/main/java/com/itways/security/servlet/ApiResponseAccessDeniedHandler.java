package com.itways.security.servlet;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itways.common.constants.ErrorCodes;
import com.itways.common.response.ApiResponse;
import com.itways.security.core.SecurityMessages;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * "You may not": 403 {@code AUTH_403} in the {@link ApiResponse} envelope for
 * an authenticated caller that a route's rule refuses, instead of Spring
 * Security's empty 403. The companion of
 * {@link ApiResponseAuthenticationEntryPoint}; opt-in the same way.
 */
public class ApiResponseAccessDeniedHandler implements AccessDeniedHandler {

    /** The platform's generic 403 wording (PLT-28); a chain may pass a more specific reason. */
    public static final String DEFAULT_MESSAGE = SecurityMessages.PERMISSION_DENIED;

    private final SecurityErrorWriter writer;
    private final String message;

    public ApiResponseAccessDeniedHandler(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_MESSAGE);
    }

    public ApiResponseAccessDeniedHandler(ObjectMapper objectMapper, String message) {
        this.writer = new SecurityErrorWriter(objectMapper);
        this.message = message;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        writer.write(response, HttpServletResponse.SC_FORBIDDEN, message, ErrorCodes.FORBIDDEN);
    }
}
