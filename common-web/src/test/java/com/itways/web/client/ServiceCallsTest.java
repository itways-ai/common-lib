package com.itways.web.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.itways.feign.ForwardedAuthorizationResolver;
import com.itways.security.internal.InternalServiceToken;
import com.sun.net.httpserver.HttpServer;

/**
 * The service token and the caller's credential on every RestClient call to
 * another platform service (the cases of channels' JourneyDirectoryTest and
 * journey's VoiceReachTest / TemplateServiceClientTest), against a local server.
 */
@ExtendWith(OutputCaptureExtension.class)
class ServiceCallsTest {

    private static final String TOKEN = "svc-token-c3d9e1";

    private HttpServer server;
    private String base;
    /** The headers of the last request the server saw (lower-case names). */
    private final Map<String, String> seen = new ConcurrentHashMap<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            seen.clear();
            exchange.getRequestHeaders().forEach((name, values) -> seen.put(name.toLowerCase(), values.get(0)));
            if (exchange.getRequestURI().getPath().equals("/slow")) {
                try {
                    Thread.sleep(600);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        RequestContextHolder.resetRequestAttributes();
    }

    private static ServiceCalls calls(String token, RestClient.Builder bootBuilder,
            ForwardedAuthorizationResolver fallback) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (bootBuilder != null) {
            beans.addBean("restClientBuilder", bootBuilder);
        }
        if (fallback != null) {
            beans.addBean("forwardedAuthorizationResolver", fallback);
        }
        return new ServiceCalls(new InternalServiceToken(token, false), beans.getBeanProvider(RestClient.Builder.class),
                beans.getBeanProvider(ForwardedAuthorizationResolver.class));
    }

    private void serving(String authorization, String apiKey) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/journeys/1");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (apiKey != null) {
            request.addHeader(ServiceCalls.API_KEY_HEADER, apiKey);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void call(RestClient client) {
        client.get().uri("/api/x").retrieve().toBodilessEntity();
    }

    @Test
    void sendsTheServiceTokenWhenOneIsConfigured() {
        call(calls(" " + TOKEN + " ", null, null).client(base));

        assertThat(seen).containsEntry("x-service-token", TOKEN);
    }

    @Test
    void sendsNoServiceTokenWhenNoneIsConfigured() {
        for (String token : new String[] { "", "  ", null }) {
            call(calls(token, null, null).client(base));
            assertThat(seen).doesNotContainKey("x-service-token");
        }
    }

    @Test
    void neverLogsTheServiceToken(CapturedOutput output) {
        serving("Bearer caller", "key-1");
        call(calls(TOKEN, null, null).client(base));

        assertThat(seen).containsEntry("x-service-token", TOKEN);
        assertThat(output.getAll()).doesNotContain(TOKEN).doesNotContain("key-1").doesNotContain("Bearer caller");
    }

    @Test
    void theTokenStaysOnTheServiceClientNotOnTheBuilderItWasGiven() {
        RestClient.Builder boot = RestClient.builder();
        serving("Bearer caller", null);

        call(calls(TOKEN, boot, null).client(base));
        assertThat(seen).containsEntry("x-service-token", TOKEN).containsEntry("authorization", "Bearer caller");

        // A client built from the original (Telegram, Twilio, ...) carries neither.
        call(boot.baseUrl(base).build());
        assertThat(seen).doesNotContainKeys("x-service-token", "authorization");
    }

    @Test
    void forwardsTheCallersAuthorizationAndApiKeyWhenPresent() {
        ServiceCalls calls = calls(TOKEN, null, null);

        serving("Bearer caller", "key-1");
        call(calls.client(base));
        assertThat(seen).containsEntry("authorization", "Bearer caller").containsEntry("x-api-key", "key-1");

        serving(null, "key-1");
        call(calls.client(base));
        assertThat(seen).doesNotContainKey("authorization").containsEntry("x-api-key", "key-1");

        serving("Bearer caller", null);
        call(calls.client(base));
        assertThat(seen).containsEntry("authorization", "Bearer caller").doesNotContainKey("x-api-key");
    }

    @Test
    void forwardsNothingOutsideARequest() {
        call(calls(TOKEN, null, null).client(base));

        assertThat(seen).containsEntry("x-service-token", TOKEN).doesNotContainKeys("authorization", "x-api-key");
    }

    @Test
    void theFallbackResolverSuppliesTheCredentialOnlyWhenTheHeaderIsMissing() {
        ForwardedAuthorizationResolver webhook = request -> Optional.of("Bearer webhook-token");
        ServiceCalls calls = calls(TOKEN, null, webhook);

        serving(null, null);
        call(calls.client(base));
        assertThat(seen).containsEntry("authorization", "Bearer webhook-token");
        assertThat(calls.currentAuthorization()).contains("Bearer webhook-token");

        serving("Bearer caller", null);
        call(calls.client(base));
        assertThat(seen).containsEntry("authorization", "Bearer caller");
        assertThat(calls.currentAuthorization()).contains("Bearer caller");

        RequestContextHolder.resetRequestAttributes();
        assertThat(calls.currentAuthorization()).isEmpty();
    }

    @Test
    void forwardCallerServesAClientBuiltElsewhere() {
        HttpHeaders headers = new HttpHeaders();
        ServiceCalls.forwardCaller(headers, null);
        assertThat(headers).isEmpty();

        serving("Bearer caller", "key-1");
        ServiceCalls.forwardCaller(headers, null);
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer caller");
        assertThat(headers.getFirst(ServiceCalls.API_KEY_HEADER)).isEqualTo("key-1");

        serving(null, null);
        HttpHeaders withFallback = new HttpHeaders();
        calls(TOKEN, null, request -> Optional.of("Bearer webhook-token")).forwardCaller(withFallback);
        assertThat(withFallback.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer webhook-token");
    }

    @Test
    void theReadTimeoutApplies() {
        RestClient client = calls(TOKEN, null, null).client(base, Duration.ofSeconds(1), Duration.ofMillis(100));

        assertThatThrownBy(() -> client.get().uri("/slow").retrieve().toBodilessEntity())
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void credentialsNeverPrintTheirValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer caller");
        request.addHeader(ServiceCalls.API_KEY_HEADER, "key-1");

        CallerCredentials credentials = CallerCredentials.of(request, null);
        assertThat(credentials.authorization()).isEqualTo("Bearer caller");
        assertThat(credentials.apiKey()).isEqualTo("key-1");
        assertThat(credentials.isEmpty()).isFalse();
        assertThat(credentials.toString()).doesNotContain("caller").doesNotContain("key-1");
        assertThat(CallerCredentials.of(null, null)).isSameAs(CallerCredentials.NONE);
        assertThat(CallerCredentials.NONE.isEmpty()).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    static class App {

        @Bean
        InternalServiceToken internalServiceToken() {
            return new InternalServiceToken(TOKEN, false);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnServiceCalls {

        @Bean
        ServiceCalls serviceCalls(InternalServiceToken token) {
            return new ServiceCalls(token, new StaticListableBeanFactory().getBeanProvider(RestClient.Builder.class),
                    new StaticListableBeanFactory().getBeanProvider(ForwardedAuthorizationResolver.class));
        }
    }

    @Test
    void theConfigRegistersOneBeanUnlessTheServiceHasItsOwn() {
        new WebApplicationContextRunner().withUserConfiguration(App.class, ServiceCallsConfig.class)
                .run(context -> assertThat(context).hasNotFailed().hasBean("serviceCalls")
                        .hasSingleBean(ServiceCalls.class));
        new WebApplicationContextRunner()
                .withUserConfiguration(OwnServiceCalls.class, App.class, ServiceCallsConfig.class)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ServiceCalls.class));
    }
}
