package com.itways.common.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * IP literals only, never names.
 *
 * <p>
 * Everything parsed here comes from a request header, so it must never reach a
 * DNS lookup: a hop such as {@code attacker.example} would make the gateway
 * query the attacker's name server on every request. IPv4 is parsed by hand;
 * IPv6 goes to {@link InetAddress#getByName} only when it contains a colon and
 * starts with a hex digit or a colon, which the JDK treats as a literal and
 * never looks up. The same rule as auth-service's {@code ClientRequestInfo}.
 */
public final class IpLiterals {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:][0-9A-Fa-f:.]{1,44}$");

    private IpLiterals() {
    }

    /** The address of an IPv4 or IPv6 literal, or {@code null} when {@code text} is anything else. */
    public static InetAddress parse(String text) {
        if (text == null) {
            return null;
        }
        if (IPV4.matcher(text).matches()) {
            return parseIpv4(text);
        }
        if (text.indexOf(':') >= 0 && IPV6.matcher(text).matches()) {
            try {
                return InetAddress.getByName(text);
            } catch (UnknownHostException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * One X-Forwarded-For hop as an IP literal, or {@code null} when it is not
     * one ({@code unknown}, a name, garbage). Proxies differ in how they write a
     * hop, so surrounding quotes, IPv6 brackets and a port are removed first:
     * {@code "[2001:db8::1]:443"} becomes {@code 2001:db8::1} and
     * {@code 203.0.113.9:5060} becomes {@code 203.0.113.9}.
     */
    public static String normalizeHop(String hop) {
        if (hop == null) {
            return null;
        }
        String value = hop.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close < 0) {
                return null;
            }
            value = value.substring(1, close);
        } else if (value.indexOf(':') > 0 && value.indexOf(':') == value.lastIndexOf(':')
                && value.indexOf('.') > 0) {
            // a.b.c.d:port — a single colon never occurs in an IPv6 literal.
            value = value.substring(0, value.indexOf(':'));
        }
        return parse(value) != null ? value : null;
    }

    private static InetAddress parseIpv4(String text) {
        String[] parts = text.split("\\.");
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(parts[i]);
            if (octet > 255) {
                return null;
            }
            bytes[i] = (byte) octet;
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
