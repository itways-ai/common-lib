package com.itways.common.correlation;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The request id that follows one user request through the platform (ARC-25):
 * the api-gateway accepts or creates it and sends it on as
 * {@value #HEADER}; every servlet service puts it in the logging context
 * ({@value #MDC_KEY}), on its outbound calls and on the messages it publishes
 * ({@value #AMQP_HEADER}); the listener that consumes such a message puts it
 * back in its own logging context; an error envelope quotes it as
 * {@code reference}.
 *
 * <p>
 * JDK only, so the reactive api-gateway uses the same rule as the servlet
 * services: an incoming id is kept when it is well formed (1 to 64 letters,
 * digits, {@code .}, {@code _} or {@code -}); anything else (missing, blank,
 * too long, with spaces, quotes or line breaks that could forge a log line) is
 * replaced by a new random UUID.
 */
public final class RequestIds {

    /** The HTTP header, on requests and on responses. */
    public static final String HEADER = "X-Request-Id";

    /** The key in the logging context (SLF4J MDC): {@code %X{requestId}} in a log pattern. */
    public static final String MDC_KEY = "requestId";

    /** The AMQP message header (lower case, as RabbitMQ header names usually are). */
    public static final String AMQP_HEADER = "x-request-id";

    /** The servlet request attribute that holds the id of the request being served. */
    public static final String REQUEST_ATTRIBUTE = "com.itways.requestId";

    /** At most 64 characters; a UUID has 36. */
    public static final int MAX_LENGTH = 64;

    private static final Pattern WELL_FORMED = Pattern.compile("^[A-Za-z0-9._-]{1," + MAX_LENGTH + "}$");

    private RequestIds() {
    }

    /** Whether {@code id} may be used as it is: non-null and 1 to 64 of {@code [A-Za-z0-9._-]}. */
    public static boolean isWellFormed(String id) {
        return id != null && WELL_FORMED.matcher(id).matches();
    }

    /** A new id: a random UUID. */
    public static String generate() {
        return UUID.randomUUID().toString();
    }

    /**
     * The id to use for a request that arrived with {@code incoming}: the value,
     * trimmed, when that is well formed; a new one otherwise.
     */
    public static String accept(String incoming) {
        if (incoming != null) {
            String trimmed = incoming.trim();
            if (isWellFormed(trimmed)) {
                return trimmed;
            }
        }
        return generate();
    }
}
