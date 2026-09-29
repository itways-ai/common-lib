package com.itways.common.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class TrustedProxiesTest {

    private final TrustedProxies defaults = TrustedProxies.of(List.of("127.0.0.0/8", "::1/128", "172.16.0.0/12"));

    @Test
    void matchesCidrRanges() {
        assertThat(defaults.contains("127.0.0.1")).isTrue();
        assertThat(defaults.contains("127.255.255.254")).isTrue();
        assertThat(defaults.contains("172.16.0.1")).isTrue();
        assertThat(defaults.contains("172.31.255.255")).isTrue();
        assertThat(defaults.contains("::1")).isTrue();
        assertThat(defaults.contains("0:0:0:0:0:0:0:1")).isTrue();

        assertThat(defaults.contains("172.32.0.1")).isFalse();
        assertThat(defaults.contains("172.15.255.255")).isFalse();
        assertThat(defaults.contains("192.168.65.1")).isFalse();
        assertThat(defaults.contains("::2")).isFalse();
    }

    @Test
    void singleAddressesAndOddPrefixes() {
        TrustedProxies proxies = TrustedProxies.of(List.of("10.1.2.3", "192.168.0.0/23", " "));

        assertThat(proxies.contains("10.1.2.3")).isTrue();
        assertThat(proxies.contains("10.1.2.4")).isFalse();
        assertThat(proxies.contains("192.168.1.200")).isTrue();
        assertThat(proxies.contains("192.168.2.1")).isFalse();
    }

    @Test
    void anythingButALiteralIsNeverTrusted() {
        assertThat(defaults.contains("localhost")).isFalse();
        assertThat(defaults.contains("127.0.0.1.nip.io")).isFalse();
        assertThat(defaults.contains("999.0.0.1")).isFalse();
        assertThat(defaults.contains("")).isFalse();
        assertThat(defaults.contains((String) null)).isFalse();
    }

    @Test
    void refusesNamesAndBadPrefixesAtStartup() {
        assertThatThrownBy(() -> TrustedProxies.of(List.of("nginx")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names are not allowed");
        assertThatThrownBy(() -> TrustedProxies.of(List.of("10.0.0.0/33")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrustedProxies.of(List.of("10.0.0.0/x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesForwardedForHops() {
        assertThat(IpLiterals.normalizeHop(" 203.0.113.9 ")).isEqualTo("203.0.113.9");
        assertThat(IpLiterals.normalizeHop("203.0.113.9:5060")).isEqualTo("203.0.113.9");
        assertThat(IpLiterals.normalizeHop("[2001:db8::1]:443")).isEqualTo("2001:db8::1");
        assertThat(IpLiterals.normalizeHop("\"[2001:db8::1]\"")).isEqualTo("2001:db8::1");
        assertThat(IpLiterals.normalizeHop("2001:db8::1")).isEqualTo("2001:db8::1");
        assertThat(IpLiterals.normalizeHop("unknown")).isNull();
        assertThat(IpLiterals.normalizeHop("attacker.example")).isNull();
        assertThat(IpLiterals.normalizeHop(".:1")).isNull();
        assertThat(IpLiterals.normalizeHop("[2001:db8::1")).isNull();
    }
}
