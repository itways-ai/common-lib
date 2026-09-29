package com.itways.security.servlet;

import com.itways.common.net.ClientIp;
import com.itways.common.net.TrustedProxies;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The client IP of a servlet request, for audit rows and per-client limits
 * (ARC-11; replaces account-service's {@code ClientIpResolver} and
 * auth-service's {@code ClientRequestInfo}). The rule is {@link ClientIp}'s,
 * the gateway's: every {@code X-Forwarded-For} line, hops normalised, garbage
 * skipped, the header read only when the peer is one of our proxies.
 *
 * <p>
 * The proxies come from {@code itways.client-ip.trusted-proxies} (default: the
 * {@code TRUSTED_PROXIES} environment variable, else
 * {@value #DEFAULT_TRUSTED_PROXIES}), as comma-separated IP literals and CIDR
 * ranges; a name instead of a literal fails at startup, as in the gateway.
 * Registered with {@code @EnableCustomSecurity}; it has no side effects.
 */
@Component("clientIpResolver")
public class ClientIpResolver {

    public static final String PROPERTY = "itways.client-ip.trusted-proxies";
    public static final String DEFAULT_TRUSTED_PROXIES = "127.0.0.0/8,::1/128,172.16.0.0/12";
    /** Column size of the audit tables' {@code ip_address}. */
    public static final int MAX_LENGTH = 64;

    private final TrustedProxies trustedProxies;

    @Autowired
    public ClientIpResolver(
            @Value("${" + PROPERTY + ":${TRUSTED_PROXIES:" + DEFAULT_TRUSTED_PROXIES + "}}") String trustedProxies) {
        this(parse(trustedProxies));
    }

    public ClientIpResolver(TrustedProxies trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    /** The client behind {@code request}, at most {@value #MAX_LENGTH} characters. */
    public String resolve(HttpServletRequest request) {
        return ClientIp.resolve(forwardedFor(request), request.getRemoteAddr(), trustedProxies, MAX_LENGTH);
    }

    /** The client of the request being served on this thread; empty outside one (scheduled work, consumers). */
    public Optional<String> current() {
        return currentRequest().map(this::resolve);
    }

    public TrustedProxies trustedProxies() {
        return trustedProxies;
    }

    /** Comma-separated entries, trimmed, blanks ignored; a name fails with the property named. */
    static TrustedProxies parse(String commaSeparated) {
        List<String> entries = commaSeparated == null ? List.of() : Arrays.asList(commaSeparated.split(","));
        try {
            return TrustedProxies.of(entries);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(PROPERTY + " (TRUSTED_PROXIES): " + e.getMessage(), e);
        }
    }

    private static List<String> forwardedFor(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders(ClientIp.FORWARDED_FOR_HEADER);
        return values == null ? List.of() : Collections.list(values);
    }

    private static Optional<HttpServletRequest> currentRequest() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                return Optional.ofNullable(servletAttributes.getRequest());
            }
        } catch (RuntimeException ignored) {
            // A recycled or already-completed request: treat as "no request".
        }
        return Optional.empty();
    }
}
