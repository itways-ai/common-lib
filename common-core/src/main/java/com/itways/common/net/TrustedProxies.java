package com.itways.common.net;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * The peers allowed to tell the gateway who the client is: our own reverse
 * proxies (the portal's nginx, a load balancer), as IP literals and CIDR
 * ranges ({@code gateway.client-ip.trusted-proxies}, env
 * {@code TRUSTED_PROXIES}, or the older {@code GATEWAY_TRUSTED_PROXIES}).
 *
 * <p>
 * Names are refused at startup: a trust list must never depend on DNS.
 */
public final class TrustedProxies {

    private final List<Range> ranges;

    private TrustedProxies(List<Range> ranges) {
        this.ranges = List.copyOf(ranges);
    }

    /**
     * Parses entries such as {@code 127.0.0.0/8}, {@code ::1/128} or
     * {@code 10.0.0.7}. Blank entries are skipped.
     *
     * @throws IllegalArgumentException for an entry that is not an IP literal or
     *                                  CIDR range
     */
    public static TrustedProxies of(List<String> entries) {
        List<Range> ranges = new ArrayList<>();
        for (String entry : entries) {
            if (entry != null && !entry.isBlank()) {
                ranges.add(Range.parse(entry.trim()));
            }
        }
        return new TrustedProxies(ranges);
    }

    /** Whether {@code ip} (an IP literal) is one of our proxies; {@code false} for anything that is not a literal. */
    public boolean contains(String ip) {
        InetAddress address = IpLiterals.parse(ip);
        return address != null && contains(address);
    }

    public boolean contains(InetAddress address) {
        return ranges.stream().anyMatch(range -> range.matches(address));
    }

    public boolean isEmpty() {
        return ranges.isEmpty();
    }

    private record Range(byte[] network, int prefixLength) {

        static Range parse(String entry) {
            int slash = entry.indexOf('/');
            String address = slash < 0 ? entry : entry.substring(0, slash);
            InetAddress parsed = IpLiterals.parse(address);
            if (parsed == null) {
                throw new IllegalArgumentException(
                        "Trusted proxy '" + entry + "' is not an IP address or CIDR range (names are not allowed)");
            }
            byte[] network = parsed.getAddress();
            int maxBits = network.length * 8;
            int prefix = maxBits;
            if (slash >= 0) {
                try {
                    prefix = Integer.parseInt(entry.substring(slash + 1));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Trusted proxy '" + entry + "' has an invalid prefix length");
                }
                if (prefix < 0 || prefix > maxBits) {
                    throw new IllegalArgumentException("Trusted proxy '" + entry + "' has an invalid prefix length");
                }
            }
            return new Range(network, prefix);
        }

        boolean matches(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
