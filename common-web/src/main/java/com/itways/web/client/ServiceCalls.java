package com.itways.web.client;

import com.itways.feign.ForwardedAuthorizationResolver;
import com.itways.security.internal.InternalServiceToken;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * How a service calls the platform's other services over {@code RestClient}
 * (ARC-11; the one version of channels' and journey's {@code ServiceHttp},
 * account's {@code ForwardedCaller} and template's private copy).
 *
 * <p>
 * Every call says which <em>caller</em> it is: when {@code itways.internal-token}
 * is set, the client sends it as {@value InternalServiceToken#HEADER} on every
 * call ({@link InternalServiceToken#headerValue()}, trimmed, nothing when
 * blank), so the service called can admit it on its {@code /internal/} routes.
 * Every call also carries the current caller's own credential
 * ({@code Authorization}, or the {@link ForwardedAuthorizationResolver}'s
 * answer, and {@code X-API-KEY}; {@link ForwardedCallerInterceptor}), so the
 * other service answers for the caller's account only, never on this
 * service's authority. Neither is ever logged.
 *
 * <p>
 * Not a global {@code RestClientCustomizer} on purpose: Spring Boot's shared
 * builder also builds the clients that call Telegram, Twilio and the AI
 * providers, and the service token and the caller's credential must never
 * reach a third-party host. {@link #builder()} works on a <em>clone</em> of
 * Boot's builder, so the headers stay on the clients built here. Timeouts are
 * short ({@link #DEFAULT_CONNECT_TIMEOUT} / {@link #DEFAULT_READ_TIMEOUT})
 * because a call happens inside a user's request.
 *
 * <p>
 * Registered as {@code serviceCalls} with {@code @EnableCustomSecurity}
 * ({@link ServiceCallsConfig}); a service that declares its own bean keeps it.
 */
public class ServiceCalls {

    public static final String API_KEY_HEADER = "X-API-KEY";
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    private final InternalServiceToken internalServiceToken;
    private final ObjectProvider<RestClient.Builder> builders;
    private final ObjectProvider<ForwardedAuthorizationResolver> fallbacks;

    /**
     * @param builders  Spring Boot's {@code RestClient.Builder} when the service
     *                  has one (its customizers apply); {@code RestClient.builder()}
     *                  otherwise
     * @param fallbacks the service's {@link ForwardedAuthorizationResolver}, if
     *                  it registers one; looked up on every call
     */
    public ServiceCalls(InternalServiceToken internalServiceToken, ObjectProvider<RestClient.Builder> builders,
            ObjectProvider<ForwardedAuthorizationResolver> fallbacks) {
        this.internalServiceToken = internalServiceToken;
        this.builders = builders;
        this.fallbacks = fallbacks;
    }

    /**
     * A builder for a client to another platform service: a clone of Boot's
     * builder (or a fresh one), with the service token as a default header when
     * one is configured, and the {@link ForwardedCallerInterceptor}. The caller
     * sets the base URL and, when the defaults do not fit, the request factory.
     */
    public RestClient.Builder builder() {
        RestClient.Builder boot = builders.getIfAvailable();
        RestClient.Builder builder = boot != null ? boot.clone() : RestClient.builder();
        String token = internalServiceToken.headerValue();
        if (token != null) {
            builder.defaultHeader(InternalServiceToken.HEADER, token);
        }
        builder.requestInterceptor(new ForwardedCallerInterceptor(fallbacks::getIfAvailable));
        return builder;
    }

    /** A client for one service with the default timeouts. */
    public RestClient client(String baseUrl) {
        return client(baseUrl, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    /** A client for one service: {@link #builder()} on the JDK request factory with these timeouts. */
    public RestClient client(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Puts the current caller's credential on {@code headers}, with this service's fallback resolver. */
    public void forwardCaller(HttpHeaders headers) {
        forwardCaller(headers, fallbacks.getIfAvailable());
    }

    /**
     * Puts the current caller's credential on {@code headers}: {@code Authorization}
     * from the request being served (else from {@code fallbackOrNull}) and
     * {@code X-API-KEY}, each only when present; nothing outside a request. For
     * a client built without the interceptor: {@code .headers(serviceCalls::forwardCaller)}.
     */
    public static void forwardCaller(HttpHeaders headers, ForwardedAuthorizationResolver fallbackOrNull) {
        CallerCredentials credentials = CallerCredentials.current(fallbackOrNull);
        if (credentials.authorization() != null) {
            headers.set(HttpHeaders.AUTHORIZATION, credentials.authorization());
        }
        if (credentials.apiKey() != null) {
            headers.set(API_KEY_HEADER, credentials.apiKey());
        }
    }

    /** The {@code Authorization} value the current caller would be forwarded with; empty outside a request. */
    public Optional<String> currentAuthorization() {
        return Optional.ofNullable(CallerCredentials.current(fallbacks.getIfAvailable()).authorization());
    }
}
