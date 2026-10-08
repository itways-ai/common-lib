package com.itways.web.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * F02: provider downloads are read as a stream and refused past the cap, whether
 * the server declares the size up front or not.
 */
class BoundedDownloadsTest {

    private HttpServer server;
    private String base;
    private final RestTemplate rest = providerRestTemplate();

    /** What conversation-service's ProviderHttpConfig builds: the JDK client with short timeouts. */
    private static RestTemplate providerRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new RestTemplate(factory);
    }

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/small", ex -> {
            byte[] body = "hello".getBytes();
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/declared-large", ex -> {
            ex.sendResponseHeaders(200, 4096);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(new byte[4096]);
            } catch (Exception ignored) {
                // the client hangs up once it sees the length
            }
        });
        server.createContext("/chunked-large", ex -> {
            ex.sendResponseHeaders(200, 0); // chunked: no Content-Length
            try (OutputStream out = ex.getResponseBody()) {
                for (int i = 0; i < 64; i++) {
                    out.write(new byte[1024]);
                }
            } catch (Exception ignored) {
                // the client hangs up at the cap
            }
        });
        server.createContext("/auth", ex -> {
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            byte[] body = String.valueOf(auth).getBytes();
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void smallBodyIsReturned() {
        assertArrayEquals("hello".getBytes(), BoundedDownloads.get(rest, base + "/small", null, 1024));
    }

    @Test
    void headersAreSent() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("AC1", "tok");
        assertArrayEquals(("Basic " + java.util.Base64.getEncoder().encodeToString("AC1:tok".getBytes())).getBytes(),
                BoundedDownloads.get(rest, base + "/auth", headers, 1024));
    }

    @Test
    void declaredLengthOverTheCapIsRefused() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> BoundedDownloads.get(rest, base + "/declared-large", null, 1024));
        assertInstanceOf(BoundedDownloads.DownloadTooLargeException.class, e);
        assertFalse(e.getMessage().contains(base), "the refusal names no URL");
    }

    @Test
    void streamedBodyOverTheCapIsRefused() {
        assertThrows(BoundedDownloads.DownloadTooLargeException.class,
                () -> BoundedDownloads.get(rest, base + "/chunked-large", null, 8 * 1024));
    }

    @Test
    void providerClientIsNotTrustAll() {
        // The JDK client with the JVM's trust store and hostname checks; not the
        // Apache client ai-engine-sdk builds with a trust-everything SSL context.
        assertInstanceOf(SimpleClientHttpRequestFactory.class, rest.getRequestFactory());
    }
}
