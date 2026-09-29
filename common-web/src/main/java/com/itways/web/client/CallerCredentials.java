package com.itways.web.client;

import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.itways.feign.ForwardedAuthorizationResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's own credential on the request being served, for an outbound
 * call to another platform service to carry on: the {@code Authorization}
 * header (or what a {@link ForwardedAuthorizationResolver} finds when there is
 * none: conversation-service's channel webhook tokens) and the {@code X-API-KEY}
 * header, each only when present. The one resolution
 * {@link ServiceCalls}, {@link ForwardedCallerInterceptor} and the Feign
 * interceptor of {@code @EnableForwardedAuth} share (ARC-11).
 *
 * <p>
 * Values are secrets: {@link #toString()} never prints them.
 */
public final class CallerCredentials {

    /** No request on this thread, or a request without either header. */
    public static final CallerCredentials NONE = new CallerCredentials(null, null);

    private final String authorization;
    private final String apiKey;

    private CallerCredentials(String authorization, String apiKey) {
        this.authorization = authorization;
        this.apiKey = apiKey;
    }

    /** The credentials of the request being served on this thread; {@link #NONE} outside one. */
    public static CallerCredentials current(ForwardedAuthorizationResolver fallbackOrNull) {
        return of(currentRequest(), fallbackOrNull);
    }

    /** The credentials of {@code request}; {@link #NONE} for {@code null}. */
    public static CallerCredentials of(HttpServletRequest request, ForwardedAuthorizationResolver fallbackOrNull) {
        if (request == null) {
            return NONE;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null && fallbackOrNull != null) {
            authorization = fallbackOrNull.resolve(request).orElse(null);
        }
        return new CallerCredentials(authorization, request.getHeader(ServiceCalls.API_KEY_HEADER));
    }

    /** The request being served on this thread, or {@code null} (scheduled work, message listeners). */
    public static HttpServletRequest currentRequest() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                return servletAttributes.getRequest();
            }
        } catch (RuntimeException ignored) {
            // A recycled or already-completed request: treat as "no request".
        }
        return null;
    }

    /** The value to send as {@code Authorization}, or {@code null}. */
    public String authorization() {
        return authorization;
    }

    /** The value to send as {@code X-API-KEY}, or {@code null}. */
    public String apiKey() {
        return apiKey;
    }

    public boolean isEmpty() {
        return authorization == null && apiKey == null;
    }

    @Override
    public String toString() {
        return "CallerCredentials[authorization=" + (authorization != null ? "present" : "none") + ", apiKey="
                + (apiKey != null ? "present" : "none") + "]";
    }
}
