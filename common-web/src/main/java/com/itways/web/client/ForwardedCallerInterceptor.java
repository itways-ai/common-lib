package com.itways.web.client;

import java.io.IOException;
import java.util.function.Supplier;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import com.itways.common.correlation.RequestIds;
import com.itways.feign.ForwardedAuthorizationResolver;
import com.itways.web.correlation.CurrentRequestId;

/**
 * Puts the caller's credential ({@link CallerCredentials}) on every request a
 * {@code RestClient} (or {@code RestTemplate}) sends, so the service called
 * answers for the caller's account only. Added by {@link ServiceCalls#builder()};
 * a client built some other way adds it with {@code requestInterceptor(...)}.
 *
 * <p>
 * This is the one place every outbound call of a service passes: what must
 * travel with every call goes here. Next to the credential it sends the
 * current request id as {@code X-Request-Id} (ARC-25; {@link CurrentRequestId},
 * also inside a message listener), unless the request already names one.
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
        if (!request.getHeaders().containsKey(RequestIds.HEADER)) {
            CurrentRequestId.get().ifPresent(id -> request.getHeaders().set(RequestIds.HEADER, id));
        }
        return execution.execute(request, body);
    }
}
