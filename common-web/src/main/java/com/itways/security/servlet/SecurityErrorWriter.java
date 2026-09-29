package com.itways.security.servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.itways.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes a security error in the platform's {@link ApiResponse} envelope, the
 * shape every controller error and the api-gateway's AUTH_401 already have.
 */
final class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : fallbackMapper();
    }

    /** A plain mapper with java-time support, for when the service has none to share. */
    static ObjectMapper fallbackMapper() {
        return new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    void write(HttpServletResponse response, int status, String message, String errorCode) throws IOException {
        // Too late to change anything (an SSE stream, a half-written body): leave it.
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(message, errorCode));
    }
}
