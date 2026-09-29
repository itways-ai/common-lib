package com.itways.security.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.itways.common.response.ApiResponse;
import com.itways.web.correlation.CurrentRequestId;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps a service's {@code /internal/} routes off the public edge (ARC-11; the
 * one version of the filter account, channels, journey and template-service
 * each carried).
 *
 * <p>
 * The gateway forwards a service's whole {@code /api/...} prefix, so the routes
 * only other platform services should reach live under an {@code internal}
 * path segment, and this filter answers any of them that arrived through a
 * proxy with the same 404 an unknown path gets: {@value #NOT_FOUND_MESSAGE} /
 * {@value #NOT_FOUND_CODE}, the body of {@code GlobalExceptionHandler},
 * {@code CustomErrorController} and the api-gateway, so a probe cannot even
 * confirm that the route exists. The tell is the proxy headers: the gateway
 * (and nginx in front of it) always adds {@code X-Forwarded-For} /
 * {@code X-Forwarded-Host} / {@code Forwarded}, while a container-to-container
 * call carries none of them. The list is configurable
 * ({@code itways.internal-guard.proxy-headers}); the presence of any one of
 * them counts, whatever its value.
 *
 * <p>
 * The proxy headers alone are a heuristic: anyone who reaches the port without
 * passing the gateway sends none. So a direct call to an internal path is also
 * checked for the platform's service token ({@link InternalServiceToken},
 * {@code itways.internal-token}): while {@code itways.internal-token-enforce}
 * is false a call without a valid token is only logged; once true it gets the
 * same 404.
 *
 * <p>
 * Registered by {@link InternalEndpointGuardConfig} ahead of Spring Security,
 * so a proxied caller learns nothing from a 401/403 either. A response that is
 * already committed is left alone. Like every error body, the 404 quotes the
 * request id as {@code reference} when there is one (ARC-25; the request-id
 * filter runs first), as the real 404 does.
 */
@Slf4j
public class InternalEndpointGuard extends OncePerRequestFilter {

    /** The headers a proxy adds; any one of them marks a proxied request. */
    public static final List<String> DEFAULT_PROXY_HEADERS = List.of("X-Forwarded-For", "X-Forwarded-Host",
            "Forwarded");

    /** The real-404 body: what an unknown path gets, so the route is not confirmed. */
    public static final String NOT_FOUND_MESSAGE = "The requested resource was not found";
    public static final String NOT_FOUND_CODE = "NOT_FOUND";

    /** An {@code internal} path segment, whatever follows it (a slash, a matrix parameter or nothing). */
    private static final Pattern INTERNAL_SEGMENT = Pattern.compile(".*/internal(/.*|;.*)?");

    private final InternalServiceToken internalServiceToken;
    private final List<String> proxyHeaders;
    private final Supplier<ObjectMapper> objectMappers;

    private volatile ObjectMapper objectMapper;

    /** With the default proxy headers and a fixed mapper (tests, manual wiring). */
    public InternalEndpointGuard(InternalServiceToken internalServiceToken, ObjectMapper objectMapper) {
        this(internalServiceToken, () -> objectMapper, DEFAULT_PROXY_HEADERS);
    }

    /**
     * @param objectMappers the service's mapper when it has exactly one; a plain
     *                      one with java-time support otherwise. Resolved lazily:
     *                      filters are created before much else.
     * @param proxyHeaders  the headers whose presence marks a proxied request;
     *                      trimmed, blanks ignored, at least one
     */
    public InternalEndpointGuard(InternalServiceToken internalServiceToken, ObjectProvider<ObjectMapper> objectMappers,
            List<String> proxyHeaders) {
        this(internalServiceToken, () -> objectMappers.getIfUnique(), proxyHeaders);
    }

    private InternalEndpointGuard(InternalServiceToken internalServiceToken, Supplier<ObjectMapper> objectMappers,
            List<String> proxyHeaders) {
        this.internalServiceToken = internalServiceToken;
        this.objectMappers = objectMappers;
        this.proxyHeaders = proxyHeaders == null ? List.of()
                : proxyHeaders.stream().filter(h -> h != null && !h.isBlank()).map(String::trim).toList();
        if (this.proxyHeaders.isEmpty()) {
            throw new IllegalArgumentException("itways.internal-guard.proxy-headers must name at least one header "
                    + "(default: " + String.join(",", DEFAULT_PROXY_HEADERS) + ")");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isInternalPath(request) && !admitted(request)) {
            refuse(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Whether a request for an internal path may go on: never through a proxy;
     * directly, when {@link InternalServiceToken#admits} says so (log-only until
     * enforced).
     */
    private boolean admitted(HttpServletRequest request) {
        if (cameThroughProxy(request, proxyHeaders)) {
            log.warn("Refused proxied request for internal path {} {}", request.getMethod(), request.getRequestURI());
            return false;
        }
        return internalServiceToken.admits(request);
    }

    private void refuse(HttpServletResponse response) throws IOException {
        // Too late to change anything (an SSE stream, a half-written body): leave it.
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper().writeValue(response.getOutputStream(),
                CurrentRequestId.stamp(ApiResponse.error(NOT_FOUND_MESSAGE, NOT_FOUND_CODE)));
    }

    /**
     * Checks both the raw URI and the container-decoded path, so percent-encoded
     * spellings such as {@code /%69nternal/} are caught as well.
     */
    public static boolean isInternalPath(HttpServletRequest request) {
        String decoded = request.getServletPath() + (request.getPathInfo() != null ? request.getPathInfo() : "");
        return matchesInternal(request.getRequestURI()) || matchesInternal(decoded);
    }

    private static boolean matchesInternal(String path) {
        return path != null && INTERNAL_SEGMENT.matcher(path.toLowerCase(Locale.ROOT)).matches();
    }

    /**
     * Whether the request passed a proxy (the gateway, nginx), judged by the
     * {@link #DEFAULT_PROXY_HEADERS}; also template-service's render-lane tell.
     */
    public static boolean cameThroughProxy(HttpServletRequest request) {
        return cameThroughProxy(request, DEFAULT_PROXY_HEADERS);
    }

    /** Whether any of {@code proxyHeaders} is present, whatever its value. */
    public static boolean cameThroughProxy(HttpServletRequest request, List<String> proxyHeaders) {
        return proxyHeaders.stream().anyMatch(header -> request.getHeader(header) != null);
    }

    /** The headers this instance treats as a proxy's. */
    public List<String> proxyHeaders() {
        return proxyHeaders;
    }

    private ObjectMapper objectMapper() {
        ObjectMapper mapper = objectMapper;
        if (mapper == null) {
            mapper = objectMappers.get();
            if (mapper == null) {
                mapper = new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }
            objectMapper = mapper;
        }
        return mapper;
    }
}
