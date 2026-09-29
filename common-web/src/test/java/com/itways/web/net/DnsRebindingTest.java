package com.itways.web.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.itways.common.net.PublicUrlPolicy;
import com.sun.net.httpserver.HttpServer;

/**
 * SPC-03: a connection to a tenant's URL goes to the address that was checked,
 * never to one the name answers afterwards.
 *
 * <p>
 * Two local servers on the same port stand in for the two answers: 127.0.0.2
 * plays the public address (the only one {@code allowed} accepts) and
 * 127.0.0.1 the private one. The DNS stub answers public on the first lookup
 * and private on every later one — the rebinding attack. The private server
 * must never see a request.
 */
class DnsRebindingTest {

    private static final String HOST = "identity.example.test";

    private InetAddress vettedAddress;
    private InetAddress privateAddress;
    private HttpServer vetted;
    private HttpServer internal;
    private final AtomicInteger vettedHits = new AtomicInteger();
    private final AtomicInteger internalHits = new AtomicInteger();
    private int port;

    @BeforeEach
    void servers() throws IOException {
        vettedAddress = InetAddress.getByName("127.0.0.2");
        privateAddress = InetAddress.getByName("127.0.0.1");
        try {
            vetted = HttpServer.create(new InetSocketAddress(vettedAddress, 0), 0);
            port = vetted.getAddress().getPort();
            internal = HttpServer.create(new InetSocketAddress(privateAddress, port), 0);
        } catch (IOException e) {
            // 127.0.0.2 is routable on Linux (where the build runs), not on every laptop.
            assumeTrue(false, "needs 127.0.0.2 on the loopback interface: " + e.getMessage());
        }
        vetted.createContext("/", exchange -> {
            vettedHits.incrementAndGet();
            if (exchange.getRequestURI().getPath().equals("/redirect")) {
                exchange.getResponseHeaders().add("Location", "http://inside.example.test:" + port + "/secret");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
                return;
            }
            byte[] body = "{\"userToken\":\"from-the-vetted-address\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        internal.createContext("/", exchange -> {
            internalHits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        vetted.start();
        internal.start();
    }

    @AfterEach
    void stop() {
        if (vetted != null) {
            vetted.stop(0);
        }
        if (internal != null) {
            internal.stop(0);
        }
    }

    @Test
    void aNameThatAnswersPublicThenPrivateIsDialledAtTheAddressThatWasChecked() {
        AtomicInteger lookups = new AtomicInteger();
        PublicUrlPolicy.Resolver rebinding = host -> lookups.incrementAndGet() == 1
                ? new InetAddress[] { vettedAddress }
                : new InetAddress[] { privateAddress };
        RestClient client = RestClient.builder().requestFactory(PinnedHttpClients.requestFactory(
                new PublicOnlyDnsResolver(rebinding, vettedAddress::equals), Duration.ofSeconds(2),
                Duration.ofSeconds(5), false)).build();

        String body = client.post().uri("http://" + HOST + ":" + port + "/exchange").body("{}").retrieve()
                .body(String.class);

        assertThat(body).contains("from-the-vetted-address");
        assertThat(lookups).hasValue(1);
        assertThat(vettedHits).hasValue(1);
        assertThat(internalHits).hasValue(0);
    }

    @Test
    void aNameWithAnyPrivateAddressIsNotDialledAtAll() {
        PublicUrlPolicy.Resolver mixed = host -> new InetAddress[] { vettedAddress, privateAddress };
        RestClient client = RestClient.builder().requestFactory(PinnedHttpClients.requestFactory(
                new PublicOnlyDnsResolver(mixed, vettedAddress::equals), Duration.ofSeconds(2),
                Duration.ofSeconds(5), false)).build();

        assertThatThrownBy(() -> client.post().uri("http://" + HOST + ":" + port + "/exchange").body("{}")
                .retrieve().body(String.class))
                .satisfies(e -> assertThat(PublicOnlyDnsResolver.isUnresolvedOrRefused(e)).isTrue());
        assertThat(vettedHits).hasValue(0);
        assertThat(internalHits).hasValue(0);
    }

    @Test
    void aRedirectToAPrivateAddressIsRefusedLikeADirectCall() throws IOException {
        PublicUrlPolicy.Resolver dns = host -> List.of(HOST).contains(host)
                ? new InetAddress[] { vettedAddress }
                : new InetAddress[] { privateAddress };
        try (CloseableHttpClient client = PinnedHttpClients.client(
                new PublicOnlyDnsResolver(dns, vettedAddress::equals), Duration.ofSeconds(2), Duration.ofSeconds(5),
                true)) {
            assertThatThrownBy(() -> client.execute(new HttpGet("http://" + HOST + ":" + port + "/redirect"),
                    response -> response.getCode()))
                    .isInstanceOf(PublicOnlyDnsResolver.RefusedAddressException.class);
        }
        assertThat(vettedHits).hasValue(1);
        assertThat(internalHits).hasValue(0);
    }
}
