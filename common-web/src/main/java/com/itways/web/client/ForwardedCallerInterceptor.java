package com.itways.web.client;

import java.io.IOException;
import java.util.function.Supplier;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import com.itways.feign.ForwardedAuthorizationResolver;

/**
 * Puts the caller's credential ({@link CallerCredentials}) on every request a
 * {@code RestClient} (or {@code RestTemplate}) sends, so the service called
 * answers for the caller's account only. Added by {@link ServiceCalls#builder()};
 * a client built some other way adds it with {@code requestInterceptor(...)}.
 *
 * <p>
 * This is the one place every outbound call of a service passes: what must
 * travel with every call goes here (ARC-25 adds the request id,
 * {@code X-Request-Id}, next to the credential).
 */
public class ForwardedCallerInterceptor implements ClientHttpRequestInterceptor {

    private final Supplier<ForwardedAuthorizationResolver> fallback;

    /** No second source for the credential: the {@code Authorization} header or nothing. */
    public ForwardedCallerInterceptor() {
        this(() -> null);
    }

    /**
     * @param fallback where to find the credential when the request carries no
     *                 {@code Authorization} header; asked on every call, may
     *                 answer {@code null}
     */
    public ForwardedCallerInterceptor(Supplier<ForwardedAuthorizationResolver> fallback) {
        this.fallback = fallback;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        ServiceCalls.forwardCaller(request.getHeaders(), fallback.get());
        // ARC-25: the request id goes on request.getHeaders() here, next to the credential.
        return execution.execute(request, body);
    }
}
