package com.itways.web.correlation;

import java.util.Optional;

import org.slf4j.MDC;

import com.itways.common.correlation.RequestIds;
import com.itways.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The request id of the work being done on this thread (ARC-25): the request
 * {@link RequestIdFilter} is serving, or the message a listener is handling
 * (common-messaging's {@code RequestIdListenerAdvice}). Read from the logging
 * context ({@link RequestIds#MDC_KEY}), so it is whatever the log lines of this
 * thread show.
 */
public final class CurrentRequestId {

    private CurrentRequestId() {
    }

    /** The id in the logging context; empty when there is none (or it is blank). */
    public static Optional<String> get() {
        String id = MDC.get(RequestIds.MDC_KEY);
        return id == null || id.isBlank() ? Optional.empty() : Optional.of(id);
    }

    /**
     * {@link #get()}, else the id {@link RequestIdFilter} stored on {@code request}
     * ({@link RequestIds#REQUEST_ATTRIBUTE}): for code that runs after the filter
     * has restored the logging context, such as a container's error page when the
     * filter is not registered for error dispatches.
     */
    public static Optional<String> of(HttpServletRequest request) {
        Optional<String> current = get();
        if (current.isPresent() || request == null) {
            return current;
        }
        Object stored = request.getAttribute(RequestIds.REQUEST_ATTRIBUTE);
        return stored instanceof String id && RequestIds.isWellFormed(id) ? Optional.of(id) : Optional.empty();
    }

    /**
     * Sets {@code body}'s {@code reference} to the current id when there is one
     * and the body has no reference yet; returns the body. The one step every
     * error envelope the library writes goes through.
     */
    public static <T> ApiResponse<T> stamp(ApiResponse<T> body) {
        return stamp(body, get());
    }

    /** As {@link #stamp(ApiResponse)}, with the id of {@link #of(HttpServletRequest)}. */
    public static <T> ApiResponse<T> stamp(ApiResponse<T> body, HttpServletRequest request) {
        return stamp(body, of(request));
    }

    private static <T> ApiResponse<T> stamp(ApiResponse<T> body, Optional<String> id) {
        if (body != null && body.getReference() == null) {
            id.ifPresent(body::setReference);
        }
        return body;
    }
}
