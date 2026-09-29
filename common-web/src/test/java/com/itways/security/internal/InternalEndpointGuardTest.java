package com.itways.security.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The guard the four services carried, now one (ARC-11): the cases of
 * channels', journey's, template's and account's guard tests, plus the
 * reconciled body and the knobs.
 */
@ExtendWith(OutputCaptureExtension.class)
class InternalEndpointGuardTest {

    private static final String TOKEN = "svc-token-b81c55";
    private static final String INTERNAL = "/api/channels/internal/active";
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    /** The status the guard answers, or 200 when it lets the request through. */
    private static int run(InternalServiceToken serviceToken, String proxyHeader, String presented) throws Exception {
        MockHttpServletRequest request = request(INTERNAL);
        if (proxyHeader != null) {
            request.addHeader(proxyHeader, "203.0.113.9");
        }
        if (presented != null) {
            request.addHeader(InternalServiceToken.HEADER, presented);
        }
        return run(new InternalEndpointGuard(serviceToken, MAPPER), request);
    }

    private static int run(InternalEndpointGuard guard, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        guard.doFilter(request, response, chain);
        return chain.getRequest() == null ? response.getStatus() : 200;
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
        r.setServletPath(uri);
        return r;
    }

    private static MockHttpServletRequest proxied(String uri, String header) {
        MockHttpServletRequest r = request(uri);
        r.addHeader(header, "203.0.113.9");
        return r;
    }

    @Test
    void internalPathsAreRecognisedHoweverSpelt() {
        assertThat(InternalEndpointGuard.isInternalPath(request("/api/channels/internal/abc"))).isTrue();
        assertThat(InternalEndpointGuard.isInternalPath(request("/api/channels/INTERNAL/abc"))).isTrue();
        assertThat(InternalEndpointGuard.isInternalPath(request("/api/channels/internal;x=1"))).isTrue();
        assertThat(InternalEndpointGuard.isInternalPath(request("/api/channels/internal"))).isTrue();
        assertThat(InternalEndpointGuard.isInternalPath(request("/api/channels/abc"))).isFalse();
        assertThat(InternalEndpointGuard.isInternalPath(request("/api/channels/internalish"))).isFalse();
        assertThat(InternalEndpointGuard.isInternalPath(request("/api/journeys/internalize"))).isFalse();
    }

    @Test
    void anEncodedInternalSegmentIsCaughtToo() throws Exception {
        // The container decodes the servlet path; the raw URI keeps the %69.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/account/ai-configs/%69nternal/active");
        request.setServletPath("/api/account/ai-configs/internal/active");
        request.addHeader("X-Forwarded-For", "203.0.113.9");
        assertThat(InternalEndpointGuard.isInternalPath(request)).isTrue();
        assertThat(run(new InternalEndpointGuard(new InternalServiceToken("", false), MAPPER), request)).isEqualTo(404);

        // And the raw URI alone is enough (a container that leaves the servlet path encoded).
        MockHttpServletRequest raw = new MockHttpServletRequest("GET", "/api/x/internal/y");
        raw.setServletPath("/other");
        assertThat(InternalEndpointGuard.isInternalPath(raw)).isTrue();
    }

    @Test
    void anyProxyHeaderMarksAProxiedRequest() {
        MockHttpServletRequest direct = request("/api/channels/internal/abc");
        assertThat(InternalEndpointGuard.cameThroughProxy(direct)).isFalse();
        for (String header : new String[] { "X-Forwarded-For", "X-Forwarded-Host", "Forwarded" }) {
            assertThat(InternalEndpointGuard.cameThroughProxy(proxied("/api/channels/internal/abc", header))).isTrue();
        }
        // Presence counts, whatever the value.
        MockHttpServletRequest empty = request("/api/channels/internal/abc");
        empty.addHeader("Forwarded", "");
        assertThat(InternalEndpointGuard.cameThroughProxy(empty)).isTrue();
    }

    @Test
    void withNoTokenConfiguredEveryDirectCallIsServed() throws Exception {
        InternalServiceToken none = new InternalServiceToken("", false);

        assertThat(run(none, null, null)).isEqualTo(200);
        assertThat(run(none, "X-Forwarded-For", null)).isEqualTo(404);
    }

    @Test
    void untilEnforcedADirectCallWithoutTheServiceTokenIsLoggedAndServed(CapturedOutput output) throws Exception {
        InternalServiceToken logOnly = new InternalServiceToken(TOKEN, false);

        assertThat(run(logOnly, null, null)).isEqualTo(200);
        assertThat(run(logOnly, null, "wrong")).isEqualTo(200);
        assertThat(run(logOnly, null, TOKEN)).isEqualTo(200);
        assertThat(output.getAll())
                .contains("Internal call with no service token", "Internal call with a wrong service token")
                .doesNotContain(TOKEN);
    }

    @Test
    void onceEnforcedOnlyTheServiceTokenIsServed(CapturedOutput output) throws Exception {
        InternalServiceToken enforced = new InternalServiceToken(TOKEN, true);

        assertThat(run(enforced, null, TOKEN)).isEqualTo(200);
        assertThat(run(enforced, null, null)).isEqualTo(404);
        assertThat(run(enforced, null, "wrong")).isEqualTo(404);
        assertThat(run(enforced, "X-Forwarded-For", TOKEN)).isEqualTo(404);
        assertThat(output.getAll()).doesNotContain(TOKEN)
                .contains("Refused proxied request for internal path GET " + INTERNAL);
    }

    @Test
    void proxiedInternalRequestsAreHidden() throws Exception {
        InternalEndpointGuard guard = new InternalEndpointGuard(new InternalServiceToken(TOKEN, true), MAPPER);

        assertThat(run(guard, proxied("/api/journeys/internal/instances/x", "X-Forwarded-For"))).isEqualTo(404);
        assertThat(run(guard, proxied("/api/journeys/internal", "Forwarded"))).isEqualTo(404);
        assertThat(run(guard, proxied("/api/journeys/INTERNAL/instances/x", "X-Forwarded-Host"))).isEqualTo(404);
        assertThat(run(guard, proxied("/api/journeys/internal;x=1/instances", "X-Forwarded-For"))).isEqualTo(404);
    }

    @Test
    void directCallsAndPublicPathsPassThrough() throws Exception {
        InternalEndpointGuard guard = new InternalEndpointGuard(new InternalServiceToken("", false), MAPPER);

        assertThat(run(guard, request("/api/journeys/internal/instances/x"))).isEqualTo(200);
        assertThat(run(guard, proxied("/api/journeys", "X-Forwarded-For"))).isEqualTo(200);
        assertThat(run(guard, proxied("/api/journeys/internalize", "X-Forwarded-For"))).isEqualTo(200);
        assertThat(run(guard, proxied("/api/templates/42/render", "X-Forwarded-For"))).isEqualTo(200);
    }

    @Test
    void theRefusalIsTheReal404Body() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new InternalEndpointGuard(new InternalServiceToken("", false), MAPPER)
                .doFilter(proxied(INTERNAL, "X-Forwarded-For"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("error");
        assertThat(body.get("message").asText()).isEqualTo("The requested resource was not found");
        assertThat(body.get("errorCode").asText()).isEqualTo("NOT_FOUND");
        assertThat(body.get("data").isNull()).isTrue();
        assertThat(body.has("timestamp")).isTrue();
    }

    @Test
    void aCommittedResponseIsLeftAlone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.getWriter().write("data: streaming");
        response.flushBuffer();

        new InternalEndpointGuard(new InternalServiceToken("", false), MAPPER)
                .doFilter(proxied(INTERNAL, "X-Forwarded-For"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("data: streaming");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theProxyHeadersAreConfigurable() throws Exception {
        ObjectProvider<ObjectMapper> mappers = mock(ObjectProvider.class);
        when(mappers.getIfUnique()).thenReturn(MAPPER);
        InternalEndpointGuard guard = new InternalEndpointGuard(new InternalServiceToken("", false), mappers,
                List.of(" X-Real-IP ", "", "Forwarded"));

        assertThat(guard.proxyHeaders()).containsExactly("X-Real-IP", "Forwarded");
        assertThat(run(guard, proxied(INTERNAL, "X-Real-IP"))).isEqualTo(404);
        assertThat(run(guard, proxied(INTERNAL, "Forwarded"))).isEqualTo(404);
        assertThat(run(guard, proxied(INTERNAL, "X-Forwarded-For"))).isEqualTo(200);
        // The static tell keeps the defaults (template-service's render lane).
        assertThat(InternalEndpointGuard.cameThroughProxy(proxied(INTERNAL, "X-Forwarded-For"))).isTrue();
        assertThat(InternalEndpointGuard.cameThroughProxy(proxied(INTERNAL, "X-Real-IP"))).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEmptyHeaderListIsRefused() {
        ObjectProvider<ObjectMapper> mappers = mock(ObjectProvider.class);
        assertThatThrownBy(() -> new InternalEndpointGuard(new InternalServiceToken("", false), mappers, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itways.internal-guard.proxy-headers");
        assertThatThrownBy(() -> new InternalEndpointGuard(new InternalServiceToken("", false), mappers, List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theFallbackMapperIsUsedWhenTheServiceHasNone() throws Exception {
        ObjectProvider<ObjectMapper> mappers = mock(ObjectProvider.class);
        when(mappers.getIfUnique()).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new InternalEndpointGuard(new InternalServiceToken("", false), mappers,
                InternalEndpointGuard.DEFAULT_PROXY_HEADERS)
                .doFilter(proxied(INTERNAL, "X-Forwarded-For"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(MAPPER.readTree(response.getContentAsString()).get("errorCode").asText()).isEqualTo("NOT_FOUND");
    }
}
