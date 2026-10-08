package com.itways.security.core;

/**
 * The platform's wording for its generic 401 and 403 answers (PLT-28): the one
 * place these messages are written. The shared servlet handlers
 * ({@code ApiResponseAuthenticationEntryPoint},
 * {@code ApiResponseAccessDeniedHandler}), common-lib's
 * {@code GlobalExceptionHandler}, auth-service and the api-gateway all answer
 * with these, so a client sees the same text whichever layer refused it.
 *
 * <p>
 * The error codes ({@code AUTH_401}, {@code AUTH_403}) are the contract; the
 * portal keys on the status. A route with a more specific reason (a runtime
 * credential on a console route, a signed-out session, an invalid token at the
 * edge) keeps its own message and the same code.
 *
 * <p>
 * Framework-free, so it ships in the {@code security-core} jar the api-gateway
 * uses.
 */
public final class SecurityMessages {

    /** 401 {@code AUTH_401}: no credential, or none that could be verified. */
    public static final String AUTHENTICATION_REQUIRED = "Authentication is required";

    /** 403 {@code AUTH_403}: a genuine caller that this route refuses. */
    public static final String PERMISSION_DENIED = "You do not have permission to perform this action";

    private SecurityMessages() {
    }
}
