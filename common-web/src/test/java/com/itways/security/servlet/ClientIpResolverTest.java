package com.itways.security.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The servlet adapter of the client-IP rule: account-service's and
 * auth-service's resolver tests, and how the trusted proxies are configured.
 * Deliberate change from account-service: a hop that is not an IP literal is
 * skipped instead of being returned as the client.
 */
class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver("127.0.0.0/8, ::1/128, 172.16.0.0/12");

    @AfterEach
    void noRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static MockHttpServletRequest request(String remoteAddr, String... forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        for (String line : forwardedFor) {
            request.addHeader("X-Forwarded-For", line);
        }
        return request;
    }

    @Test
    void aForgedFirstHopIsIgnored() {
        assertThat(resolver.resolve(request("172.18.0.5", "1.2.3.4, 203.0.113.9, 172.18.0.2")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void aDirectCallUsesTheSocketAddress() {
        assertThat(resolver.resolve(request("198.51.100.7"))).isEqualTo("198.51.100.7");
        assertThat(resolver.resolve(request("198.51.100.7", "1.2.3.4"))).isEqualTo("198.51.100.7");
    }

    @Test
    void aCallFromInsideThePlatformKeepsTheFirstHop() {
        assertThat(resolver.resolve(request("172.18.0.5", "172.18.0.9"))).isEqualTo("172.18.0.9");
    }

    @Test
    void garbageInTheHeaderIsSkipped() {
        // account-service answered "evil.example.com" here.
        assertThat(resolver.resolve(request("172.18.0.5", "evil.example.com"))).isEqualTo("172.18.0.5");
        assertThat(resolver.resolve(request("172.18.0.5", "203.0.113.9, unknown"))).isEqualTo("203.0.113.9");
    }

    @Test
    void everyHeaderLineIsRead() {
        assertThat(resolver.resolve(request("172.18.0.5", "203.0.113.66", "192.168.65.1"))).isEqualTo("192.168.65.1");
    }

    @Test
    void theResultIsCutToTheColumnSize() {
        assertThat(resolver.resolve(request("x".repeat(100)))).hasSize(ClientIpResolver.MAX_LENGTH);
    }

    @Test
    void theCurrentRequestIsResolvedFromTheHolder() {
        assertThat(resolver.current()).isEmpty();

        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request("172.18.0.5", "1.2.3.4, 203.0.113.9")));
        assertThat(resolver.current()).contains("203.0.113.9");

        RequestContextHolder.resetRequestAttributes();
        assertThat(resolver.current()).isEmpty();
    }

    @Test
    void blanksAreIgnoredAndNamesFailAtStartup() {
        assertThat(new ClientIpResolver("127.0.0.0/8,, ,::1/128").trustedProxies().contains("127.0.0.1")).isTrue();
        assertThat(new ClientIpResolver("").trustedProxies().isEmpty()).isTrue();
        assertThatThrownBy(() -> new ClientIpResolver("127.0.0.0/8, nginx"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itways.client-ip.trusted-proxies")
                .hasMessageContaining("names are not allowed");
    }

    @Test
    void theDefaultListApplies() {
        new ApplicationContextRunner().withUserConfiguration(ClientIpResolver.class).run(context -> {
            assertThat(context).hasNotFailed().hasBean("clientIpResolver");
            ClientIpResolver bean = context.getBean(ClientIpResolver.class);
            assertThat(bean.trustedProxies().contains("172.16.0.1")).isTrue();
            assertThat(bean.trustedProxies().contains("127.0.0.1")).isTrue();
            assertThat(bean.trustedProxies().contains("::1")).isTrue();
            assertThat(bean.trustedProxies().contains("10.0.0.1")).isFalse();
        });
    }

    @Test
    void theEnvironmentVariableIsTheFallbackAndThePropertyWins() {
        new ApplicationContextRunner().withUserConfiguration(ClientIpResolver.class)
                .withPropertyValues("TRUSTED_PROXIES=10.0.0.0/8").run(context -> {
                    ClientIpResolver bean = context.getBean(ClientIpResolver.class);
                    assertThat(bean.trustedProxies().contains("10.0.0.1")).isTrue();
                    assertThat(bean.trustedProxies().contains("127.0.0.1")).isFalse();
                });
        new ApplicationContextRunner().withUserConfiguration(ClientIpResolver.class)
                .withPropertyValues("TRUSTED_PROXIES=10.0.0.0/8", "itways.client-ip.trusted-proxies=192.168.0.0/16")
                .run(context -> {
                    ClientIpResolver bean = context.getBean(ClientIpResolver.class);
                    assertThat(bean.trustedProxies().contains("192.168.1.1")).isTrue();
                    assertThat(bean.trustedProxies().contains("10.0.0.1")).isFalse();
                });
    }

    @Test
    void aNameInThePropertyFailsTheStartup() {
        new ApplicationContextRunner().withUserConfiguration(ClientIpResolver.class)
                .withPropertyValues("itways.client-ip.trusted-proxies=nginx")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("names are not allowed"));
    }
}
