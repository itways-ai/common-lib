package com.itways.common.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The one allow-list rule (exact host, IP literal, {@code .domain} suffix) and
 * the ways a host can be written to slip past a naive string compare. Nothing
 * here resolves a name.
 */
class HostAllowListTest {

    private final HostAllowList list = HostAllowList
            .parse(" api.partner.local, .corp.internal, 10.20.30.40, [fd00::5], MÜNCHEN.example. ,, ");

    @Test
    void entriesAreNormalisedAndKeptInOrder() {
        assertThat(list.entries()).containsExactly("api.partner.local", "10.20.30.40", "fd00:0:0:0:0:0:0:5",
                "xn--mnchen-3ya.example", ".corp.internal");
        assertThat(list.isEmpty()).isFalse();
        assertThat(list).hasToString("HostAllowList" + list.entries());
    }

    @ParameterizedTest(name = "{0} allowed={1}")
    @CsvSource({
            // exact host: case, trailing dot, surrounding space do not matter
            "api.partner.local, true", "API.Partner.LOCAL, true", "api.partner.local., true",
            "'  api.partner.local  ', true",
            // not the same host
            "x.api.partner.local, false", "partner.local, false", "api.partner.local.evil.example, false",
            "api-partner.local, false",
            // .domain suffix: the domain itself and everything under it, nothing that merely ends alike
            "corp.internal, true", "svc.corp.internal, true", "a.b.c.corp.internal, true", "CORP.INTERNAL., true",
            "notcorp.internal, false", "corp.internal.evil.example, false", "internal, false",
            // IP literals, in any spelling of the same address
            "10.20.30.40, true", "fd00::5, true", "[fd00::5], true", "FD00:0:0:0:0:0:0:5, true",
            "[FD00:0000:0000:0000:0000:0000:0000:0005], true",
            "10.20.30.41, false", "fd00::6, false",
            // IDN: the Unicode and the ASCII form of the same name
            "münchen.example, true", "xn--mnchen-3ya.example, true", "XN--MNCHEN-3YA.EXAMPLE., true",
            "muenchen.example, false" })
    void hostsAreJudgedInTheirNormalisedForm(String host, boolean allowed) {
        assertThat(list.allows(host)).isEqualTo(allowed);
    }

    /**
     * Ways to write an address that a naive compare, or a lenient resolver, might
     * take for a listed one. None of them is the entry, so none is allowed.
     */
    @ParameterizedTest
    @ValueSource(strings = { "127.1", "127.0.1", "2130706433", "0x7f000001", "0x7f.0.0.1", "0177.0.0.1",
            "127.000.000.001", "127.0.0.1.evil.example", "127.0.0.1:8080", "127.0.0.1/x", "127.0.0.1%20",
            "127.0.0.1@evil.example", "evil.example@127.0.0.1", "127.0.0.1*" })
    void addressShorthandAndDecorationsNeverMatchAnAddressEntry(String host) {
        HostAllowList loopback = HostAllowList.parse("127.0.0.1");

        assertThat(loopback.allows(host)).as(host).isFalse();
        // The one exception that is the same address: the IPv4-mapped IPv6 spelling the JDK reads as IPv4.
        assertThat(loopback.allows("[::ffff:127.0.0.1]")).isTrue();
        assertThat(loopback.allows("::ffff:127.0.0.1")).isTrue();
    }

    @Test
    void aSuffixNeverMatchesAnIpLiteral() {
        HostAllowList suffix = HostAllowList.parse(".30.40, .example");

        assertThat(suffix.allows("10.20.30.40")).isFalse();
        assertThat(suffix.allows("x.30.40")).as("a name under that odd domain is still a name").isTrue();
        assertThat(suffix.allows("api.example")).isTrue();
    }

    @Test
    void aUrlIsJudgedByItsHostOnly() {
        assertThat(list.allows(URI.create("https://api.partner.local/x?y=1"))).isTrue();
        assertThat(list.allows(URI.create("http://API.PARTNER.LOCAL:8443/"))).as("the port is not part of the rule")
                .isTrue();
        assertThat(list.allows(URI.create("https://[fd00::5]:8443/x"))).isTrue();
        assertThat(list.allows(URI.create("https://svc.corp.internal"))).isTrue();
        // The user-info trick: the host is what follows the @.
        assertThat(list.allows(URI.create("https://api.partner.local@evil.example/"))).isFalse();
        assertThat(list.allows(URI.create("https://api.partner.local:443@evil.example/"))).isFalse();
        // No host at all.
        assertThat(list.allows(URI.create("mailto:a@api.partner.local"))).isFalse();
        assertThat(list.allows(URI.create("/relative/path"))).isFalse();
        assertThat(list.allows((URI) null)).isFalse();
        // A different host behind the listed one.
        assertThat(list.allows(URI.create("https://evil.example/api.partner.local"))).isFalse();
        assertThat(list.allows(URI.create("https://evil.example/?u=api.partner.local"))).isFalse();
    }

    @Test
    void nothingIsAllowedByAnEmptyList() {
        for (HostAllowList empty : List.of(HostAllowList.EMPTY, HostAllowList.parse(null), HostAllowList.parse(""),
                HostAllowList.parse(" , ,"), HostAllowList.of(null), HostAllowList.of(List.of()),
                HostAllowList.of(Arrays.asList(null, "", "  ")))) {
            assertThat(empty.isEmpty()).isTrue();
            assertThat(empty.entries()).isEmpty();
            assertThat(empty.allows("api.partner.local")).isFalse();
            assertThat(empty.allows("")).isFalse();
            assertThat(empty.allows((String) null)).isFalse();
        }
    }

    @Test
    void nullBlankAndNonHostsAreNotAllowed() {
        assertThat(list.allows((String) null)).isFalse();
        assertThat(list.allows("")).isFalse();
        assertThat(list.allows("   ")).isFalse();
        assertThat(list.allows(".")).isFalse();
        assertThat(list.allows("api.partner.local/x")).isFalse();
        assertThat(list.allows("api.partner.local:443")).isFalse();
        assertThat(list.allows("https://api.partner.local")).isFalse();
        assertThat(list.allows("api partner.local")).isFalse();
        assertThat(list.allows("[fd00::5")).isFalse();
    }

    /** A misread setting fails when the list is built, not by silently never matching. */
    @ParameterizedTest
    @ValueSource(strings = { "https://api.example.com", "api.example.com/x", "user@api.example.com",
            "api.example.com:8443", "10.20.30.40:8443", "api example.com", ".", "..", "*.example.com", "*",
            "api.*.example", "[fd00::5", "fd00::5]", "10.20.30.256", ".10.20.30.40", ".[fd00::5]", "a..b" })
    void anEntryThatIsNotAHostIsRefused(String entry) {
        assertThatThrownBy(() -> HostAllowList.parse("ok.example, " + entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'" + entry + "'");
        assertThatThrownBy(() -> HostAllowList.of(List.of(entry))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aWildcardEntryNamesTheSuffixFormToUseInstead() {
        assertThatThrownBy(() -> HostAllowList.parse("*.corp.internal"))
                .hasMessageContaining("'*.corp.internal'").hasMessageContaining("'.corp.internal'");
    }

    @ParameterizedTest(name = "normalize({0}) = {1}")
    @CsvSource(nullValues = "NULL", value = {
            "' API.Partner.Local. ', api.partner.local",
            "[FD00::5], fd00:0:0:0:0:0:0:5",
            "fd00:0:0:0:0:0:0:5, fd00:0:0:0:0:0:0:5",
            "::ffff:10.0.0.1, 10.0.0.1",
            "10.0.0.1, 10.0.0.1",
            "münchen.example, xn--mnchen-3ya.example",
            "xn--mnchen-3ya.example, xn--mnchen-3ya.example",
            "127.1, 127.1",
            "2130706433, 2130706433",
            "010.0.0.1, 010.0.0.1",
            "NULL, NULL",
            "'', NULL",
            "'   ', NULL",
            "., NULL",
            "host:8443, NULL",
            "host/path, NULL",
            "user@host, NULL",
            "a b, NULL",
            "[::1, NULL",
            "300.1.1.1, NULL" })
    void normalizeGivesTheComparedForm(String host, String expected) {
        assertThat(HostAllowList.normalize(host)).isEqualTo(expected);
    }

    /** The lists the three existing copies accept keep their meaning here. */
    @Test
    void theExistingSettingsParseAsBefore() {
        HostAllowList journey = HostAllowList.parse("api.partner.local, .corp.internal");
        assertThat(journey.allows("api.partner.local")).isTrue();
        assertThat(journey.allows("svc.corp.internal")).isTrue();
        assertThat(journey.allows("corp.internal")).isTrue();
        assertThat(journey.allows("1.1.1.1")).isFalse();

        HostAllowList resolver = HostAllowList.of(Arrays.asList(" Intranet.Example ", ".corp.example", "", null));
        assertThat(resolver.allows("intranet.example")).isTrue();
        assertThat(resolver.allows("INTRANET.EXAMPLE.")).isTrue();
        assertThat(resolver.allows("api.corp.example")).isTrue();
        assertThat(resolver.allows("corp.example")).isTrue();
        assertThat(resolver.allows("other.example")).isFalse();
        assertThat(resolver.allows("notcorp.example")).isFalse();
        assertThat(resolver.allows("[intranet.example]")).isTrue();
    }
}
