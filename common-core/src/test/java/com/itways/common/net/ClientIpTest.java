package com.itways.common.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The gateway's client-IP rule as a pure function (the cases of the gateway's
 * {@code ClientAddressFilterTest} and account-service's and auth-service's
 * resolver tests). One deliberate change from account/auth: a hop that is not
 * an IP literal is skipped, never returned as the client.
 */
class ClientIpTest {

    private static final TrustedProxies DEFAULTS = TrustedProxies.of(
            List.of("127.0.0.0/8", "::1/128", "172.16.0.0/12"));

    private static String resolve(String forwardedFor, String peer) {
        return ClientIp.resolve(forwardedFor == null ? null : List.of(forwardedFor), peer, DEFAULTS);
    }

    @Test
    void anUntrustedPeerIsTheClientWhateverItSent() {
        assertThat(resolve("1.2.3.4", "203.0.113.9")).isEqualTo("203.0.113.9");
        assertThat(resolve("1.2.3.4, 172.19.0.5", "203.0.113.9")).isEqualTo("203.0.113.9");
        assertThat(resolve(null, "198.51.100.7")).isEqualTo("198.51.100.7");
    }

    @Test
    void aTrustedProxyGivesTheRightMostUntrustedHop() {
        assertThat(resolve("203.0.113.66, 192.168.65.1", "172.19.0.13")).isEqualTo("192.168.65.1");
    }

    @Test
    void aForgedFirstHopIsIgnored() {
        // The client wrote 1.2.3.4 itself; nginx appended the real address, the
        // gateway called us from inside the Docker network.
        assertThat(resolve("1.2.3.4, 203.0.113.9, 172.18.0.2", "172.18.0.5")).isEqualTo("203.0.113.9");
    }

    @Test
    void aTrustedPeerWithoutAHeaderIsTheClientItself() {
        assertThat(resolve(null, "172.19.0.13")).isEqualTo("172.19.0.13");
        assertThat(ClientIp.resolve(List.of(), "172.19.0.13", DEFAULTS)).isEqualTo("172.19.0.13");
        assertThat(ClientIp.resolve(List.of(""), "172.19.0.13", DEFAULTS)).isEqualTo("172.19.0.13");
    }

    @Test
    void aCallFromInsideThePlatformKeepsTheFirstHop() {
        assertThat(resolve("172.18.0.9", "172.18.0.5")).isEqualTo("172.18.0.9");
        assertThat(resolve("172.19.0.5, 127.0.0.1", "172.19.0.13")).isEqualTo("172.19.0.5");
    }

    @Test
    void garbageAndNamesAreSkippedNeverResolved() {
        assertThat(resolve("192.168.65.1, attacker.example, unknown, 999.1.1.1, 172.19.0.5", "172.19.0.13"))
                .isEqualTo("192.168.65.1");
        // account-service returned "evil.example.com" here; a name is not a client address.
        assertThat(resolve("evil.example.com", "172.18.0.5")).isEqualTo("172.18.0.5");
        assertThat(resolve("unknown, 172.18.0.2", "172.18.0.5")).isEqualTo("172.18.0.2");
    }

    @Test
    void hopsWithPortsAndBracketsAreNormalized() {
        assertThat(resolve("198.51.100.7:5060", "172.19.0.13")).isEqualTo("198.51.100.7");
        assertThat(resolve("\"[2001:db8::1]:443\"", "172.19.0.13")).isEqualTo("2001:db8::1");
        assertThat(resolve("2001:db8::2", "172.19.0.13")).isEqualTo("2001:db8::2");
    }

    @Test
    void everyHeaderLineIsRead() {
        assertThat(ClientIp.resolve(List.of("203.0.113.66", "192.168.65.1"), "172.19.0.13", DEFAULTS))
                .isEqualTo("192.168.65.1");
        assertThat(ClientIp.resolve(List.of("192.168.65.1", "172.19.0.5"), "172.19.0.13", DEFAULTS))
                .isEqualTo("192.168.65.1");
        assertThat(ClientIp.resolve(Arrays.asList(null, "192.168.65.1"), "172.19.0.13", DEFAULTS))
                .isEqualTo("192.168.65.1");
    }

    @Test
    void loopbackPeersAreTrustedByDefault() {
        assertThat(resolve("192.168.65.1", "::1")).isEqualTo("192.168.65.1");
        assertThat(resolve("192.168.65.1", "0:0:0:0:0:0:0:1")).isEqualTo("192.168.65.1");
        assertThat(resolve("192.168.65.1", "127.0.0.1")).isEqualTo("192.168.65.1");
    }

    @Test
    void anUnknownPeerStaysUnknown() {
        assertThat(resolve("1.2.3.4", null)).isNull();
        assertThat(ClientIp.resolve(List.of("1.2.3.4"), "172.19.0.13", null)).isEqualTo("172.19.0.13");
        // A peer that is not a literal cannot be trusted, so it is the client as given.
        assertThat(resolve("1.2.3.4", "localhost")).isEqualTo("localhost");
    }

    @Test
    void theResultIsCutToTheColumnSize() {
        String tooLong = "x".repeat(100);
        assertThat(ClientIp.resolve(null, tooLong, DEFAULTS, 64)).hasSize(64);
        assertThat(ClientIp.resolve(List.of("192.168.65.1"), "172.19.0.13", DEFAULTS, 64)).isEqualTo("192.168.65.1");
        assertThat(ClientIp.resolve(null, null, DEFAULTS, 64)).isNull();
        assertThat(ClientIp.truncate("abc", 2)).isEqualTo("ab");
        assertThat(ClientIp.truncate("abc", 3)).isEqualTo("abc");
    }

    @Test
    void hopsListsEveryUsableHopInOrder() {
        assertThat(ClientIp.hops(List.of(" 1.2.3.4 , unknown, [2001:db8::1]:443", "5.6.7.8:80")))
                .containsExactly("1.2.3.4", "2001:db8::1", "5.6.7.8");
        assertThat(ClientIp.hops(null)).isEmpty();
    }
}
