package com.itways.common.net;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An operator's list of hosts that an outbound call may reach even though the
 * address rule ({@link PublicUrlPolicy#isPublic}) would refuse them: a
 * partner's API on the platform's own network, a mail relay, a bank sandbox
 * behind a VPN.
 *
 * <p>
 * The one matching rule for every such list. journey-engine's
 * {@code EgressGuard} ({@code JOURNEY_API_ALLOWED_HOSTS}), notification-service's
 * {@code TenantMailGuard} ({@code NOTIFICATION_TENANT_SMTP_ALLOWED_HOSTS}) and
 * common-web's {@code PublicOnlyDnsResolver} each carried a copy of it; the
 * connectors feature adds a per-connector list and an optional platform-wide
 * bound, so the rule lives here once, framework-free, and the copies delegate
 * to it.
 *
 * <p>
 * An entry is one of three things:
 * <ul>
 * <li>an exact host name: {@code api.partner.local};</li>
 * <li>an IP literal: {@code 10.20.30.40}, {@code fd00::5} or {@code [fd00::5]};</li>
 * <li>a {@code .domain} suffix: {@code .corp.internal} matches the domain and
 * everything under it, never {@code notcorp.internal}.</li>
 * </ul>
 * Nothing else: no scheme, path, port or user name (an entry with one of them is
 * refused when the list is built, so a misread setting fails at startup rather
 * than silently never matching), and no {@code *.domain} (write
 * {@code .domain}). Ports are not part of the rule: a list names hosts, the
 * caller decides which ports it dials.
 *
 * <p>
 * Hosts and entries are compared in a normalised form ({@link #normalize}):
 * lower case, no surrounding space, no trailing dot, no IPv6 brackets, IDN
 * names as their ASCII (punycode) form, IPv6 literals in one spelling. So
 * {@code API.Partner.Local.} matches {@code api.partner.local}, and
 * {@code münchen.example} matches {@code xn--mnchen-3ya.example}. Numeric
 * shorthand ({@code 127.1}, {@code 2130706433}, {@code 0x7f000001}, octets
 * with leading zeros) is not an IP literal here, and is compared as the text
 * it is: it never matches an entry that names the address properly, which is
 * what an operator writes. A suffix entry never matches an IP literal.
 *
 * <p>
 * A URL is judged by its host only ({@link #allows(URI)}): with
 * {@code https://allowed.example@evil.example/} the host is
 * {@code evil.example}, and a redirect is a new URL the caller must judge
 * again (or, as the platform's clients do, never follow).
 */
public final class HostAllowList {

    /** The list that allows nothing. */
    public static final HostAllowList EMPTY = new HostAllowList(List.of(), List.of());

    private static final Pattern DOTTED_QUAD = Pattern
            .compile("^(0|[1-9]\\d{0,2})\\.(0|[1-9]\\d{0,2})\\.(0|[1-9]\\d{0,2})\\.(0|[1-9]\\d{0,2})$");

    private final List<String> exact;
    private final List<String> suffixes;

    private HostAllowList(List<String> exact, List<String> suffixes) {
        this.exact = List.copyOf(exact);
        this.suffixes = List.copyOf(suffixes);
    }

    /**
     * The list a setting holds: entries separated by commas, as
     * {@code JOURNEY_API_ALLOWED_HOSTS} is written. Null, blank, and blank
     * entries mean nothing.
     *
     * @throws IllegalArgumentException for an entry that is not a host name, an
     *                                  IP literal or a {@code .domain} suffix
     */
    public static HostAllowList parse(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return EMPTY;
        }
        return of(List.of(commaSeparated.split(",")));
    }

    /**
     * The list from its entries (a bound {@code List<String>} property, a stored
     * {@code text[]} column). Null and blank entries are ignored.
     *
     * @throws IllegalArgumentException for an entry that is not a host name, an
     *                                  IP literal or a {@code .domain} suffix
     */
    public static HostAllowList of(Collection<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return EMPTY;
        }
        List<String> exact = new ArrayList<>();
        List<String> suffixes = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.startsWith("*.")) {
                throw new IllegalArgumentException("allow-list entry '" + trimmed + "' is not supported: write '"
                        + trimmed.substring(1) + "' for the domain and its subdomains");
            }
            boolean suffix = trimmed.startsWith(".");
            String name = normalize(suffix ? trimmed.substring(1) : trimmed);
            if (name == null || isIpLiteral(name) && suffix) {
                throw new IllegalArgumentException("allow-list entry '" + trimmed
                        + "' is not a host name, an IP literal or a .domain suffix");
            }
            if (suffix) {
                suffixes.add("." + name);
            } else {
                exact.add(name);
            }
        }
        return exact.isEmpty() && suffixes.isEmpty() ? EMPTY : new HostAllowList(exact, suffixes);
    }

    /** Whether the list has no entries, so it allows nothing. */
    public boolean isEmpty() {
        return exact.isEmpty() && suffixes.isEmpty();
    }

    /** The entries in their normalised form: exact hosts as they are, suffixes with their leading dot. */
    public List<String> entries() {
        List<String> all = new ArrayList<>(exact);
        all.addAll(suffixes);
        return List.copyOf(all);
    }

    /**
     * Whether {@code host} (a name or an IP literal, IPv6 with or without
     * brackets) is on the list. Null, blank, and anything that is not a host
     * are not.
     */
    public boolean allows(String host) {
        String name = normalize(host);
        if (name == null) {
            return false;
        }
        if (exact.contains(name)) {
            return true;
        }
        if (isIpLiteral(name)) {
            return false;
        }
        for (String suffix : suffixes) {
            if (name.endsWith(suffix) || name.equals(suffix.substring(1))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the host of {@code uri} is on the list; a URI without a host is not. */
    public boolean allows(URI uri) {
        return uri != null && allows(uri.getHost());
    }

    /**
     * The form hosts are compared in: trimmed, lower case, no trailing dot, no
     * IPv6 brackets, an IDN name as its ASCII form, an IPv6 literal in the JDK's
     * one spelling. Null when {@code host} is null, blank, or not a host name.
     */
    public static String normalize(String host) {
        if (host == null) {
            return null;
        }
        String name = host.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("[") && name.endsWith("]")) {
            name = name.substring(1, name.length() - 1);
        }
        if (name.length() > 1 && name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('@') >= 0 || name.indexOf('?') >= 0
                || name.indexOf('#') >= 0 || name.indexOf('*') >= 0
                || name.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        if (name.startsWith(".") || name.endsWith(".") || name.contains("..")) {
            return null; // an empty label: ".", "..", ".host", "a..b" (IDN.toASCII lets "." through)
        }
        Matcher quad = DOTTED_QUAD.matcher(name);
        if (quad.matches()) {
            for (int group = 1; group <= 4; group++) {
                if (Integer.parseInt(quad.group(group)) > 255) {
                    return null;
                }
            }
            return name;
        }
        if (name.indexOf(':') >= 0) {
            // Only an IPv6 literal may hold a colon; host:port is not a host.
            InetAddress address = IpLiterals.parse(name);
            if (address == null) {
                return null;
            }
            // One spelling per address; ::ffff:a.b.c.d comes back as the IPv4 address inside.
            return address.getHostAddress();
        }
        try {
            String ascii = IDN.toASCII(name);
            return ascii.isEmpty() ? null : ascii;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "HostAllowList" + entries();
    }

    /** Whether a normalised name is an IP literal: a dotted quad, or an IPv6 spelling (it holds a colon). */
    private static boolean isIpLiteral(String normalised) {
        return normalised.indexOf(':') >= 0 || DOTTED_QUAD.matcher(normalised).matches();
    }
}
