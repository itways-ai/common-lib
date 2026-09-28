package com.itways.security.servlet;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itways.common.constants.ErrorCodes;
import com.itways.common.response.ApiResponse;
import com.itways.security.core.SecurityMessages;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * "Who are you?": 401 {@code AUTH_401} in the {@link ApiResponse} envelope for
 * a request that reaches a protected route without a valid credential (PLT-14).
 *
 * <p>
 * Without an entry point Spring Security answers an anonymous request with an
 * empty 403, the same as "you may not". The portal refreshes its session only
 * on 401, and the api-gateway already answers 401 {@code AUTH_401} with this
 * body at the edge, so a service must say the same when it is called directly.
 *
 * <p>
 * Opt-in: a service wires it into its own chain, e.g.
 * {@code http.exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))}.
 * {@code @EnableCustomSecurity} registers one as a bean unless the service has
 * its own {@link org.springframework.security.web.AuthenticationEntryPoint}
 * bean; a chain that does not reference it is unaffected.
 */
public class ApiResponseAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** The message the api-gateway and the services answer a missing credential with (PLT-28). */
    public static final String DEFAULT_MESSAGE = SecurityMessages.AUTHENTICATION_REQUIRED;

    private final SecurityErrorWriter writer;
    private final String message;

    public ApiResponseAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_MESSAGE);
    }

    public ApiResponseAuthenticationEntryPoint(ObjectMapper objectMapper, String message) {
        this.writer = new SecurityErrorWriter(objectMapper);
        this.message = message;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        writer.write(response, HttpServletResponse.SC_UNAUTHORIZED, message, ErrorCodes.UNAUTHORIZED);
    }
}
