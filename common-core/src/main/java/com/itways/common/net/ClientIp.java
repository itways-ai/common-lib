package com.itways.common.net;

import java.util.ArrayList;
import java.util.List;

/**
 * The client behind a request that may have passed our own reverse proxies:
 * the rule the api-gateway applies at the edge, for the services to apply on
 * what the gateway hands them (ARC-11).
 *
 * <p>
 * A proxy appends the address it saw to {@code X-Forwarded-For}, so the header
 * is a chain: whatever the client wrote itself on the left, then one hop per
 * proxy. The chain is read only when the peer (the socket address) is one of
 * {@link TrustedProxies}; a peer that is not one of ours is the client, and
 * anything it sent in the header is ignored. Every header line is read (a
 * proxy may add a second line instead of appending), each line is split on
 * commas and each hop is normalised by {@link IpLiterals#normalizeHop}: a hop
 * that is not an IP literal ({@code unknown}, a name, garbage) is skipped,
 * never resolved and never taken for the client. The client is the right-most
 * hop that is not one of our proxies; when every hop is ours (a call from
 * inside the platform) it is the left-most; with no usable hop at all it is
 * the peer.
 *
 * <p>
 * Framework-free: the api-gateway (WebFlux) and the servlet services share it.
 * The servlet adapter is {@code ClientIpResolver} in common-web.
 */
public final class ClientIp {

    /** The header proxies append the client to. */
    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private ClientIp() {
    }

    /**
     * The client's IP literal, or {@code peerAddress} as given when the peer is
     * not a trusted proxy, when the header holds no usable hop, or when the
     * peer is unknown ({@code null}).
     *
     * @param forwardedForHeaderValues every {@code X-Forwarded-For} header line, in
     *                                 order; {@code null} or empty when there is none
     * @param peerAddress              the socket address the request came from
     * @param trustedProxies           our own proxies
     */
    public static String resolve(List<String> forwardedForHeaderValues, String peerAddress,
            TrustedProxies trustedProxies) {
        if (peerAddress == null || trustedProxies == null || !trustedProxies.contains(peerAddress)) {
            return peerAddress;
        }
        List<String> chain = hops(forwardedForHeaderValues);
        for (int i = chain.size() - 1; i >= 0; i--) {
            if (!trustedProxies.contains(chain.get(i))) {
                return chain.get(i);
            }
        }
        return chain.isEmpty() ? peerAddress : chain.get(0);
    }

    /**
     * As {@link #resolve(List, String, TrustedProxies)}, cut to {@code maxLength}
     * characters for a bounded column (auth-service's audit rows keep 64).
     */
    public static String resolve(List<String> forwardedForHeaderValues, String peerAddress,
            TrustedProxies trustedProxies, int maxLength) {
        return truncate(resolve(forwardedForHeaderValues, peerAddress, trustedProxies), maxLength);
    }

    /**
     * Every usable hop of the header lines, in order: each line split on commas,
     * each hop normalised; anything that is not an IP literal is left out.
     */
    public static List<String> hops(List<String> forwardedForHeaderValues) {
        List<String> chain = new ArrayList<>();
        if (forwardedForHeaderValues == null) {
            return chain;
        }
        for (String header : forwardedForHeaderValues) {
            if (header == null) {
                continue;
            }
            for (String hop : header.split(",")) {
                String literal = IpLiterals.normalizeHop(hop);
                if (literal != null) {
                    chain.add(literal);
                }
            }
        }
        return chain;
    }

    static String truncate(String value, int maxLength) {
        if (value == null || maxLength < 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
