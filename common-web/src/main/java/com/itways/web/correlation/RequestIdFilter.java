package com.itways.web.correlation;

import com.itways.common.correlation.RequestIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a request id (ARC-25): the caller's {@code X-Request-Id}
 * when it is well formed (the api-gateway always sends one), a new UUID
 * otherwise ({@link RequestIds#accept}).
 *
 * <p>
 * The id is
 * <ul>
 * <li>echoed as the response's {@code X-Request-Id}, set before the rest of the
 * chain runs, so it is there however and whenever the response is committed
 * (a 401 from Spring Security, a streamed body);</li>
 * <li>stored as the request attribute {@link RequestIds#REQUEST_ATTRIBUTE};</li>
 * <li>put in the logging context as {@link RequestIds#MDC_KEY} for the
 * duration of the request, and the previous value restored afterwards (removed
 * when there was none), so a pooled thread never carries it into the next
 * request.</li>
 * </ul>
 *
 * <p>
 * It also runs on the error and async re-dispatches of a request (the
 * container's {@code /error} page, the completion of an async result), reusing
 * the id the first pass chose, so the log lines and the error body of those
 * dispatches carry the same id.
 *
 * <p>
 * Registered by {@link RequestCorrelationConfig} first of all filters.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String id = idFor(request);
        request.setAttribute(RequestIds.REQUEST_ATTRIBUTE, id);
        if (!response.isCommitted()) {
            response.setHeader(RequestIds.HEADER, id);
        }
        String previous = MDC.get(RequestIds.MDC_KEY);
        MDC.put(RequestIds.MDC_KEY, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previous != null) {
                MDC.put(RequestIds.MDC_KEY, previous);
            } else {
                MDC.remove(RequestIds.MDC_KEY);
            }
        }
    }

    /** A re-dispatch keeps the id of the first pass; a new request accepts the caller's or gets a new one. */
    static String idFor(HttpServletRequest request) {
        Object chosen = request.getAttribute(RequestIds.REQUEST_ATTRIBUTE);
        if (chosen instanceof String id && RequestIds.isWellFormed(id)) {
            return id;
        }
        return RequestIds.accept(request.getHeader(RequestIds.HEADER));
    }

    /** The completion of an async request is logged with its id too. */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /** So is the container's error page, and its body quotes the id. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
