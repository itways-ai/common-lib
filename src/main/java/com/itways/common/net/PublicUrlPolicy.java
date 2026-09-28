package com.itways.common.net;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Whether a tenant-supplied URL may be called by the platform.
 *
 * <p>
 * Some settings name an endpoint the platform itself calls — a voice line's
 * identity exchange, for one. Left unchecked, a tenant could point it at
 * {@code http://ollama:11434}, {@code http://169.254.169.254} or another
 * service's container and have the platform send requests there (SSRF). The
 * rule is: https only, a real public host name or a public IP address, and — when
 * the name resolves — only to public addresses.
 *
 * <p>
 * Use {@link #strictProblem(String, Resolver)} when a person saves a setting (a
 * readable reason, or empty when acceptable: the host must resolve, to public
 * addresses only) and {@link #requirePublic(String, Resolver)} right before
 * calling out. {@link #problem(String, Resolver)} is the lenient form for
 * checks that must not depend on DNS being reachable, such as re-checking
 * stored rows at startup. Checking at call time as well matters: a name that
 * resolved to a public address on save can point somewhere else later, and a
 * caller that resolves the name again for the connection itself leaves a
 * window between the check and the connect (DNS rebinding), so it should
 * connect to the very addresses it checked.
 */
public final class PublicUrlPolicy {

	/** Resolves a host name; {@link #SYSTEM_DNS} in production, a stub in tests. */
	@FunctionalInterface
	public interface Resolver {
		InetAddress[] resolve(String host) throws UnknownHostException;
	}

	public static final Resolver SYSTEM_DNS = InetAddress::getAllByName;

	/** Suffixes that only ever name private networks. */
	private static final List<String> PRIVATE_SUFFIXES = List.of(".localhost", ".local", ".internal", ".lan",
			".home.arpa", ".intranet", ".corp");

	private static final Pattern DOTTED_QUAD = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
	private static final Pattern LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");

	private PublicUrlPolicy() {
	}

	/**
	 * Why {@code url} may not be called, or empty when it may. When the host
	 * does not resolve (offline, or a name that is not live yet) only the
	 * syntactic rules apply; the call-time check catches it later.
	 */
	public static Optional<String> problem(String url, Resolver resolver) {
		return problem(url, resolver, false);
	}

	/**
	 * As {@link #problem(String, Resolver)}, but a host name that does not
	 * resolve is itself a problem. For the moment a person saves a setting:
	 * a name that is not live cannot be checked, and accepting it would let
	 * it be pointed at a private address after the check (SPC-03).
	 */
	public static Optional<String> strictProblem(String url, Resolver resolver) {
		return problem(url, resolver, true);
	}

	private static Optional<String> problem(String url, Resolver resolver, boolean mustResolve) {
		URI uri;
		try {
			uri = new URI(url == null ? "" : url.trim());
		} catch (URISyntaxException e) {
			return Optional.of("is not a valid URL");
		}
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			return Optional.of("must use https");
		}
		if (uri.getRawUserInfo() != null) {
			return Optional.of("must not carry a user name or password");
		}
		String host = normaliseHost(uri.getHost());
		if (host == null) {
			return Optional.of("must name a host");
		}

		if (host.startsWith("[")) {
			return literalProblem(host.substring(1, host.length() - 1));
		}
		String lastLabel = host.substring(host.lastIndexOf('.') + 1);
		if (lastLabel.chars().allMatch(Character::isDigit)) {
			// Numeric: only a plain dotted quad is an address; 127.1, 2130706433
			// and friends are shorthand that resolvers disagree on.
			return DOTTED_QUAD.matcher(host).matches() ? literalProblem(host)
					: Optional.of("must be a host name or a full IPv4 address");
		}
		if (!host.contains(".")) {
			return Optional.of("must be a public host name, not an internal one (" + host + ")");
		}
		if (host.equals("localhost") || PRIVATE_SUFFIXES.stream().anyMatch(host::endsWith)) {
			return Optional.of("must be a public host name, not an internal one (" + host + ")");
		}
		for (String label : host.split("\\.")) {
			if (!LABEL.matcher(label).matches()) {
				return Optional.of("must be a valid host name");
			}
		}

		InetAddress[] addresses;
		try {
			addresses = resolver.resolve(host);
		} catch (UnknownHostException e) {
			return mustResolve ? Optional.of("host does not resolve (" + host + ")") : Optional.empty();
		}
		if (mustResolve && addresses.length == 0) {
			return Optional.of("host does not resolve (" + host + ")");
		}
		for (InetAddress address : addresses) {
			if (!isPublic(address)) {
				return Optional.of("resolves to a private address (" + address.getHostAddress() + ")");
			}
		}
		return Optional.empty();
	}

	/**
	 * For the moment of the call: the URL must pass {@link #problem} and its host
	 * must resolve, to public addresses only.
	 *
	 * @throws IllegalArgumentException naming the reason
	 */
	public static void requirePublic(String url, Resolver resolver) {
		Optional<String> problem = strictProblem(url, resolver);
		if (problem.isPresent()) {
			throw new IllegalArgumentException("URL " + problem.get());
		}
	}

	/** False for loopback, private, link-local, carrier-grade NAT, documentation and reserved ranges. */
	public static boolean isPublic(InetAddress address) {
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return false;
		}
		byte[] b = address.getAddress();
		if (address instanceof Inet4Address) {
			int a0 = b[0] & 0xff, a1 = b[1] & 0xff, a2 = b[2] & 0xff;
			return !(a0 == 0 || a0 >= 240 // this network, reserved and broadcast
					|| (a0 == 100 && a1 >= 64 && a1 <= 127) // carrier-grade NAT
					|| (a0 == 192 && a1 == 0 && (a2 == 0 || a2 == 2)) // IETF, TEST-NET-1
					|| (a0 == 198 && (a1 == 18 || a1 == 19)) // benchmarking
					|| (a0 == 198 && a1 == 51 && a2 == 100) // TEST-NET-2
					|| (a0 == 203 && a1 == 0 && a2 == 113)); // TEST-NET-3
		}
		if (address instanceof Inet6Address) {
			if ((b[0] & 0xfe) == 0xfc) { // unique local fc00::/7
				return false;
			}
			if ((b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) {
				return false; // documentation 2001:db8::/32
			}
			boolean mapped = true; // ::ffff:a.b.c.d carries an IPv4 address
			for (int i = 0; i < 10; i++) {
				mapped &= b[i] == 0;
			}
			if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
				try {
					return isPublic(InetAddress.getByAddress(new byte[] { b[12], b[13], b[14], b[15] }));
				} catch (UnknownHostException e) {
					return false;
				}
			}
		}
		return true;
	}

	private static Optional<String> literalProblem(String literal) {
		try {
			for (String part : literal.split("\\.")) {
				if (literal.contains(".") && Integer.parseInt(part) > 255) {
					return Optional.of("must be a valid IP address");
				}
			}
			InetAddress address = InetAddress.getByName(literal); // a literal: no DNS lookup
			return isPublic(address) ? Optional.empty()
					: Optional.of("points at a private address (" + address.getHostAddress() + ")");
		} catch (UnknownHostException | NumberFormatException e) {
			return Optional.of("must be a valid IP address");
		}
	}

	private static String normaliseHost(String host) {
		if (host == null || host.isBlank()) {
			return null;
		}
		String h = host.toLowerCase(Locale.ROOT);
		if (h.endsWith(".")) {
			h = h.substring(0, h.length() - 1);
		}
		if (!h.startsWith("[")) {
			try {
				h = IDN.toASCII(h);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
		return h.isEmpty() ? null : h;
	}
}
