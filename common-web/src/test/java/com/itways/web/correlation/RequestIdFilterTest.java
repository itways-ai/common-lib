package com.itways.web.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.WebUtils;

import com.itways.common.correlation.RequestIds;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

/** The request-id filter (ARC-25): which id, where it goes, and that the thread is left clean. */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void cleanThread() {
        MDC.clear();
    }

    /** What the rest of the chain saw. */
    private record Seen(String mdc, Object attribute, String responseHeader) {
    }

    private Seen run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<Seen> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(new Seen(MDC.get(RequestIds.MDC_KEY),
                req.getAttribute(RequestIds.REQUEST_ATTRIBUTE), ((MockHttpServletResponse) res)
                        .getHeader(RequestIds.HEADER)));
        filter.doFilter(request, response, chain);
        return seen.get();
    }

    @Test
    void aWellFormedIncomingIdIsKeptEverywhere() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/journeys");
        request.addHeader(RequestIds.HEADER, " gw-5f1c2a ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Seen seen = run(request, response);

        // Visible to the rest of the chain: logging context, request attribute, and the response header already.
        assertThat(seen).isEqualTo(new Seen("gw-5f1c2a", "gw-5f1c2a", "gw-5f1c2a"));
        assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo("gw-5f1c2a");
        // Gone from the thread afterwards.
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    @Test
    void aMissingOrMalformedIdIsReplacedByANewOne() throws Exception {
        for (String incoming : new String[] { null, "", "has space", "x".repeat(65), "a\nforged" }) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
            if (incoming != null) {
                request.addHeader(RequestIds.HEADER, incoming);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();

            Seen seen = run(request, response);

            String id = response.getHeader(RequestIds.HEADER);
            assertThat(UUID.fromString(id).toString()).as(String.valueOf(incoming)).isEqualTo(id);
            assertThat(seen.mdc()).isEqualTo(id);
            assertThat(seen.attribute()).isEqualTo(id);
        }
    }

    @Test
    void thePreviousValueIsRestoredEvenWhenTheChainFails() {
        MDC.put(RequestIds.MDC_KEY, "outer");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.addHeader(RequestIds.HEADER, "inner");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(MDC.get(RequestIds.MDC_KEY)).isEqualTo("inner");
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(MDC.get(RequestIds.MDC_KEY)).isEqualTo("outer");
    }

    @Test
    void theHeaderIsThereWhenTheChainCommitsTheResponseItself() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stream");
        request.addHeader(RequestIds.HEADER, "stream-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            res.getWriter().write("data: first");
            res.flushBuffer();
        });

        assertThat(response.isCommitted()).isTrue();
        assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo("stream-1");
    }

    @Test
    void anErrorDispatchKeepsTheIdOfTheFirstPass() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(WebUtils.ERROR_REQUEST_URI_ATTRIBUTE, "/api/x");
        request.setAttribute(RequestIds.REQUEST_ATTRIBUTE, "first-pass");
        request.addHeader(RequestIds.HEADER, "ignored-now");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Seen seen = run(request, response);

        assertThat(seen.mdc()).isEqualTo("first-pass");
        assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo("first-pass");
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    @Test
    void anAsyncDispatchIsFilteredToo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.setDispatcherType(DispatcherType.ASYNC);
        request.setAttribute(RequestIds.REQUEST_ATTRIBUTE, "async-1");

        assertThat(run(request, new MockHttpServletResponse()).mdc()).isEqualTo("async-1");
    }
}
