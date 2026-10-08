package com.itways.web.net;

import com.itways.common.net.HostAllowList;
import com.itways.common.net.PublicUrlPolicy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.DnsResolver;

/**
 * DNS for calls to addresses a tenant wrote (SPC-03): resolves a host once,
 * checks every address it got against {@link PublicUrlPolicy#isPublic}, and
 * hands exactly those addresses to the connection.
 *
 * <p>
 * Checking a name and then letting the HTTP client resolve it again for the
 * connection leaves a window: a name with a short TTL can answer a public
 * address to the check and a private one to the connect (DNS rebinding), and
 * the request lands on {@code 169.254.169.254} or another service's port.
 * Installed as the connection manager's resolver, this is the only lookup the
 * connection makes, so what was checked is what is dialled. Every address
 * counts, not just the first: the client may try the next one when the first
 * does not answer.
 *
 * <p>
 * The connection still speaks to the host by its name: TLS sends it as SNI and
 * verifies the certificate against it, and the {@code Host} header carries
 * it, exactly as without this resolver.
 *
 * <p>
 * An operator may allow-list hosts the check would refuse (a tenant's system
 * inside the platform's network), as journey-engine's {@code EgressGuard} and
 * notification-service's {@code TenantMailGuard} do: an exact name, an IP
 * literal, or a {@code .domain} suffix that matches the domain and everything
 * under it; the rule is common-core's {@link HostAllowList} (2.3.0), which
 * refuses an entry that is not a host when the resolver is built. An
 * allow-listed host is dialled at whatever it resolves to. What counts as
 * public is a {@link Predicate} too, {@link PublicUrlPolicy#isPublic} by
 * default (ARC-11; moved from conversation-service, where it had no allow-list).
 */
@Slf4j
public final class PublicOnlyDnsResolver implements DnsResolver {

    /** Thrown for a host with a non-public address; an {@link UnknownHostException} so clients treat it as one. */
    public static final class RefusedAddressException extends UnknownHostException {

        private static final long serialVersionUID = 1L;

        RefusedAddressException(String host) {
            super(host + " resolves to a non-public address");
        }
    }

    private final PublicUrlPolicy.Resolver lookup;
    private final Predicate<InetAddress> allowed;
    private final HostAllowList allowedHosts;

    /** System DNS, public addresses only, no allow-list. */
    public PublicOnlyDnsResolver() {
        this(PublicUrlPolicy.SYSTEM_DNS, PublicUrlPolicy::isPublic, List.of());
    }

    /** System DNS, public addresses only, plus {@code allowedHosts} (exact names or {@code .domain} suffixes). */
    public PublicOnlyDnsResolver(Collection<String> allowedHosts) {
        this(PublicUrlPolicy.SYSTEM_DNS, PublicUrlPolicy::isPublic, allowedHosts);
    }

    /** For tests: a stub lookup, and what counts as public. */
    public PublicOnlyDnsResolver(PublicUrlPolicy.Resolver lookup, Predicate<InetAddress> allowed) {
        this(lookup, allowed, List.of());
    }

    /**
     * @param lookup       where names are resolved ({@link PublicUrlPolicy#SYSTEM_DNS} in production)
     * @param allowed      what counts as an address that may be dialled
     * @param allowedHosts hosts dialled without the check: exact names, IP literals, or
     *                     {@code .domain} suffixes ({@link HostAllowList}); case does not
     *                     matter, blanks are ignored
     * @throws IllegalArgumentException for an entry that is not a host (a URL, a
     *                                  {@code host:port}, a {@code *.domain})
     */
    public PublicOnlyDnsResolver(PublicUrlPolicy.Resolver lookup, Predicate<InetAddress> allowed,
            Collection<String> allowedHosts) {
        this.lookup = lookup;
        this.allowed = allowed;
        this.allowedHosts = HostAllowList.of(allowedHosts);
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        String name = unbracket(host);
        InetAddress[] addresses = lookup.resolve(name);
        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException(host);
        }
        if (isAllowedHost(name)) {
            return addresses.clone();
        }
        for (InetAddress address : addresses) {
            if (!allowed.test(address)) {
                // The address maps the internal network: the log has it, the message does not.
                log.warn("[EGRESS] Refused {}: it resolves to {}, which is not a public address", host,
                        address.getHostAddress());
                throw new RefusedAddressException(host);
            }
        }
        return addresses.clone();
    }

    @Override
    public String resolveCanonicalHostname(String host) {
        // Only asked for by authentication schemes these clients never use.
        return host;
    }

    /** Whether {@code host} is on the allow-list: an exact entry, or under a {@code .domain} entry (the domain itself included). */
    public boolean isAllowedHost(String host) {
        return allowedHosts.allows(host);
    }

    /** Whether {@code error}, or anything that caused it, is a host that did not resolve or was refused. */
    public static boolean isUnresolvedOrRefused(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }

    private static String unbracket(String host) {
        return host != null && host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1)
                : host;
    }
}
