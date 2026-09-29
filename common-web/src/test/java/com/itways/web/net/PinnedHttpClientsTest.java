package com.itways.web.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

/**
 * The strict settings the shared client took from journey-engine (no cookies,
 * redirects only when asked), through a stub DNS that points a name at the
 * local server.
 */
class PinnedHttpClientsTest {

    private static final String HOST = "pinned.example.test";

    private HttpServer server;
    private int port;
    private InetAddress loopback;

    @BeforeEach
    void start() throws IOException {
        loopback = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/set-cookie", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "session=abc; Path=/");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/echo-cookie", exchange -> {
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            byte[] body = String.valueOf(cookie).getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://" + HOST + ":" + port + "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            byte[] body = "arrived".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private PublicOnlyDnsResolver dns() {
        return new PublicOnlyDnsResolver(host -> new InetAddress[] { loopback }, loopback::equals);
    }

    private String url(String path) {
        return "http://" + HOST + ":" + port + path;
    }

    @Test
    void cookiesAreNeverKeptBetweenCalls() throws IOException {
        try (CloseableHttpClient client = PinnedHttpClients.client(dns(), Duration.ofSeconds(2),
                Duration.ofSeconds(5), false)) {
            client.execute(new HttpGet(url("/set-cookie")), response -> response.getCode());
            String echoed = client.execute(new HttpGet(url("/echo-cookie")),
                    response -> new String(response.getEntity().getContent().readAllBytes()));

            assertThat(echoed).isEqualTo("null");
        }
    }

    @Test
    void redirectsAreFollowedOnlyWhenAsked() throws IOException {
        try (CloseableHttpClient pinned = PinnedHttpClients.client(dns(), Duration.ofSeconds(2),
                Duration.ofSeconds(5), false)) {
            int status = pinned.execute(new HttpGet(url("/redirect")), response -> response.getCode());
            assertThat(status).isEqualTo(302);
        }
        try (CloseableHttpClient following = PinnedHttpClients.client(dns(), Duration.ofSeconds(2),
                Duration.ofSeconds(5), true, new PinnedHttpClients.PoolSize(10, 2))) {
            String body = following.execute(new HttpGet(url("/redirect")),
                    response -> new String(response.getEntity().getContent().readAllBytes()));
            assertThat(body).isEqualTo("arrived");
        }
    }

    @Test
    void theRequestFactoryServesSpringClients() {
        RestClient client = RestClient.builder()
                .requestFactory(PinnedHttpClients.requestFactory(dns(), Duration.ofSeconds(2), Duration.ofSeconds(5),
                        false, PinnedHttpClients.PoolSize.DEFAULT))
                .build();

        String body = client.get().uri(url("/target")).retrieve().body(String.class);
        assertThat(body).isEqualTo("arrived");
        HttpStatusCode status = client.get().uri(url("/redirect"))
                .exchange((request, response) -> response.getStatusCode());
        assertThat(status).isEqualTo(HttpStatus.FOUND);
    }

    @Test
    void poolSizesAreChecked() {
        assertThat(PinnedHttpClients.PoolSize.DEFAULT).isEqualTo(new PinnedHttpClients.PoolSize(25, 5));
        assertThatThrownBy(() -> new PinnedHttpClients.PoolSize(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PinnedHttpClients.PoolSize(5, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
