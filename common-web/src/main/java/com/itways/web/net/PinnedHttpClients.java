package com.itways.web.net;

import java.time.Duration;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * HTTP clients for calls to tenant-supplied URLs, whose connections go only to
 * addresses a {@link PublicOnlyDnsResolver} vetted (SPC-03; ARC-11: moved from
 * conversation-service, with journey-engine's stricter settings).
 *
 * <p>
 * Apache HttpClient 5 because it lets the connection manager take its DNS from
 * us while keeping everything else standard: the JDK client has no resolver
 * hook, and dialling a checked IP by hand would lose SNI and certificate
 * verification against the host name. TLS is the library default (system trust
 * store, default hostname verifier). No proxy is used (system properties are
 * not read), since a proxy would resolve the name itself. Cookies are never
 * kept (a tenant's server cannot make one call carry state into the next), and
 * waiting for a pooled connection is bounded by the connect timeout, so a
 * saturated pool fails fast instead of queueing a user's request.
 *
 * <p>
 * The pool is {@link PoolSize#DEFAULT} (HttpClient's own 25 total / 5 per
 * route) unless a caller passes its own; journey-engine's API calls use 200/50.
 */
public final class PinnedHttpClients {

    /** How many connections a client keeps: in all, and to one host. */
    public record PoolSize(int maxTotal, int maxPerRoute) {

        /** HttpClient's defaults. */
        public static final PoolSize DEFAULT = new PoolSize(25, 5);

        public PoolSize {
            if (maxTotal < 1 || maxPerRoute < 1 || maxPerRoute > maxTotal) {
                throw new IllegalArgumentException(
                        "pool sizes must be positive and maxPerRoute <= maxTotal; got " + maxTotal + "/" + maxPerRoute);
            }
        }
    }

    private PinnedHttpClients() {
    }

    /**
     * @param followRedirects whether to follow 3xx answers; every hop is dialled
     *                        through {@code dns} as well, so a redirect to a
     *                        private address is refused like a direct call
     */
    public static CloseableHttpClient client(DnsResolver dns, Duration connectTimeout, Duration readTimeout,
            boolean followRedirects) {
        return client(dns, connectTimeout, readTimeout, followRedirects, PoolSize.DEFAULT);
    }

    /** As {@link #client(DnsResolver, Duration, Duration, boolean)}, with this pool size. */
    public static CloseableHttpClient client(DnsResolver dns, Duration connectTimeout, Duration readTimeout,
            boolean followRedirects, PoolSize pool) {
        PoolingHttpClientConnectionManager connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(dns)
                .setMaxConnTotal(pool.maxTotal())
                .setMaxConnPerRoute(pool.maxPerRoute())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.of(connectTimeout))
                        .setSocketTimeout(Timeout.of(readTimeout))
                        .build())
                .build();
        HttpClientBuilder builder = HttpClients.custom()
                .setConnectionManager(connections)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.of(connectTimeout))
                        .setResponseTimeout(Timeout.of(readTimeout))
                        .build())
                .disableCookieManagement();
        if (!followRedirects) {
            builder.disableRedirectHandling();
        }
        return builder.build();
    }

    /** The same, for Spring's {@code RestClient} or {@code RestTemplate}. */
    public static HttpComponentsClientHttpRequestFactory requestFactory(DnsResolver dns, Duration connectTimeout,
            Duration readTimeout, boolean followRedirects) {
        return new HttpComponentsClientHttpRequestFactory(client(dns, connectTimeout, readTimeout, followRedirects));
    }

    /** As {@link #requestFactory(DnsResolver, Duration, Duration, boolean)}, with this pool size. */
    public static HttpComponentsClientHttpRequestFactory requestFactory(DnsResolver dns, Duration connectTimeout,
            Duration readTimeout, boolean followRedirects, PoolSize pool) {
        return new HttpComponentsClientHttpRequestFactory(
                client(dns, connectTimeout, readTimeout, followRedirects, pool));
    }
}
