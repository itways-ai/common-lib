package com.itways.web.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.itways.common.net.PublicUrlPolicy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** The resolver's rules, the allow-list added for ARC-11 included. */
@ExtendWith(OutputCaptureExtension.class)
class PublicOnlyDnsResolverTest {

    private static InetAddress ip(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static PublicUrlPolicy.Resolver answering(String... literals) {
        return host -> Arrays.stream(literals).map(PublicOnlyDnsResolverTest::ip).toArray(InetAddress[]::new);
    }

    @Test
    void resolvesOnceAndHandsOverEveryAddressAsACopy() throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        PublicUrlPolicy.Resolver counting = host -> {
            lookups.incrementAndGet();
            return answering("93.184.216.34", "93.184.216.35").resolve(host);
        };
        PublicOnlyDnsResolver resolver = new PublicOnlyDnsResolver(counting, PublicUrlPolicy::isPublic);

        InetAddress[] addresses = resolver.resolve("identity.example");

        assertThat(addresses).containsExactly(ip("93.184.216.34"), ip("93.184.216.35"));
        assertThat(lookups).hasValue(1);
        addresses[0] = ip("10.0.0.1");
        assertThat(resolver.resolve("identity.example")[0]).isEqualTo(ip("93.184.216.34"));
    }

    @Test
    void refusesAHostWithAnyNonPublicAddressWithoutNamingIt(CapturedOutput output) {
        PublicOnlyDnsResolver resolver = new PublicOnlyDnsResolver(answering("93.184.216.34", "10.0.0.7"),
                PublicUrlPolicy::isPublic);

        assertThatThrownBy(() -> resolver.resolve("identity.example"))
                .isInstanceOf(PublicOnlyDnsResolver.RefusedAddressException.class)
                .isInstanceOf(UnknownHostException.class)
                .hasMessageContaining("identity.example")
                .hasMessageNotContaining("10.0.0.7");
        // The log has the address, so an operator can see what the name pointed at.
        assertThat(output.getAll()).contains("[EGRESS] Refused identity.example").contains("10.0.0.7");
    }

    @Test
    void anEmptyAnswerIsAnUnknownHost() {
        for (PublicUrlPolicy.Resolver empty : new PublicUrlPolicy.Resolver[] { host -> new InetAddress[0],
                host -> null }) {
            assertThatThrownBy(() -> new PublicOnlyDnsResolver(empty, a -> true).resolve("nowhere.example"))
                    .isInstanceOf(UnknownHostException.class)
                    .isNotInstanceOf(PublicOnlyDnsResolver.RefusedAddressException.class);
        }
    }

    @Test
    void bracketsAreStrippedBeforeTheLookup() throws Exception {
        AtomicReference<String> asked = new AtomicReference<>();
        PublicUrlPolicy.Resolver recording = host -> {
            asked.set(host);
            return new InetAddress[] { ip("2001:4860:4860::8888") };
        };

        new PublicOnlyDnsResolver(recording, PublicUrlPolicy::isPublic).resolve("[2001:4860:4860::8888]");

        assertThat(asked).hasValue("2001:4860:4860::8888");
    }

    @Test
    void anAllowListedHostIsDialledWithoutTheCheck() throws Exception {
        PublicOnlyDnsResolver resolver = new PublicOnlyDnsResolver(answering("10.0.0.7"), PublicUrlPolicy::isPublic,
                Arrays.asList(" Intranet.Example ", ".corp.example", "", null));

        assertThat(resolver.resolve("intranet.example")).containsExactly(ip("10.0.0.7"));
        assertThat(resolver.resolve("INTRANET.EXAMPLE.")).containsExactly(ip("10.0.0.7"));
        assertThat(resolver.resolve("api.corp.example")).containsExactly(ip("10.0.0.7"));
        assertThat(resolver.resolve("corp.example")).containsExactly(ip("10.0.0.7"));
        assertThatThrownBy(() -> resolver.resolve("other.example"))
                .isInstanceOf(PublicOnlyDnsResolver.RefusedAddressException.class);
        assertThatThrownBy(() -> resolver.resolve("notcorp.example"))
                .isInstanceOf(PublicOnlyDnsResolver.RefusedAddressException.class);

        assertThat(resolver.isAllowedHost("[intranet.example]")).isTrue();
        assertThat(resolver.isAllowedHost(null)).isFalse();
        assertThat(new PublicOnlyDnsResolver(answering("10.0.0.7"), PublicUrlPolicy::isPublic)
                .isAllowedHost("intranet.example")).isFalse();
    }

    @Test
    void whatCountsAsPublicIsAPredicate() throws Exception {
        InetAddress loopback = ip("127.0.0.2");
        PublicOnlyDnsResolver resolver = new PublicOnlyDnsResolver(host -> new InetAddress[] { loopback },
                loopback::equals);

        assertThat(resolver.resolve("pinned.example")).containsExactly(loopback);
        assertThat(resolver.resolveCanonicalHostname("pinned.example")).isEqualTo("pinned.example");
    }

    @Test
    void unresolvedOrRefusedIsFoundAnywhereInTheCauseChain() {
        UnknownHostException refused = new UnknownHostException("x");

        assertThat(PublicOnlyDnsResolver.isUnresolvedOrRefused(refused)).isTrue();
        assertThat(PublicOnlyDnsResolver.isUnresolvedOrRefused(new RuntimeException(new IOException(refused))))
                .isTrue();
        assertThat(PublicOnlyDnsResolver.isUnresolvedOrRefused(new RuntimeException("no"))).isFalse();
        assertThat(PublicOnlyDnsResolver.isUnresolvedOrRefused(null)).isFalse();
    }
}
