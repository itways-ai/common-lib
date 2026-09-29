package com.itways.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import com.itways.common.exception.BusinessException;

/** The list a request asks for: scope parameter, else the assistant header (new name, else legacy), else all. */
@SuppressWarnings("removal") // the legacy header name is what these tests send
class RequestedScopeArgumentResolverTest {

    private static final UUID SELECTED = UUID.fromString("5f0c1a4e-0000-4000-8000-000000000001");
    private static final UUID OTHER = UUID.fromString("5f0c1a4e-0000-4000-8000-000000000002");

    private final RequestedScopeArgumentResolver resolver = new RequestedScopeArgumentResolver();

    @Test
    void theHeaderIsXAssistantId() {
        assertThat(ScopeHeaders.ASSISTANT).isEqualTo("X-Assistant-Id");
        assertThat(ScopeHeaders.LEGACY_ASSISTANT).isEqualTo("X-Nibras-Assistant");
    }

    @Test
    void theAssistantHeaderSelectsThatAssistant() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ScopeHeaders.ASSISTANT, SELECTED.toString());

        assertThat(resolve(request)).isEqualTo(ListScope.assistant(SELECTED));
    }

    @Test
    void theLegacyHeaderAloneIsStillAccepted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ScopeHeaders.LEGACY_ASSISTANT, SELECTED.toString());

        assertThat(resolve(request)).isEqualTo(ListScope.assistant(SELECTED));
    }

    @Test
    void theNewHeaderWinsOverTheLegacyOne() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ScopeHeaders.LEGACY_ASSISTANT, OTHER.toString());
        request.addHeader(ScopeHeaders.ASSISTANT, SELECTED.toString());

        assertThat(resolve(request)).isEqualTo(ListScope.assistant(SELECTED));
    }

    @Test
    void theScopeParameterWinsOverEitherHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ScopeHeaders.ASSISTANT, SELECTED.toString());
        request.addHeader(ScopeHeaders.LEGACY_ASSISTANT, SELECTED.toString());
        request.setParameter(ScopeHeaders.SCOPE_PARAM, "shared");

        assertThat(resolve(request)).isEqualTo(ListScope.SHARED);
    }

    @Test
    void neitherMeansTheWholeAccount() {
        assertThat(resolve(new MockHttpServletRequest())).isEqualTo(ListScope.ALL);
    }

    @Test
    void aMalformedLegacyHeaderIsA400LikeTheNewOne() {
        for (String header : new String[] { ScopeHeaders.ASSISTANT, ScopeHeaders.LEGACY_ASSISTANT }) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(header, "not-a-uuid");

            assertThatThrownBy(() -> resolve(request)).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not-a-uuid");
        }
    }

    private ListScope resolve(MockHttpServletRequest request) {
        return (ListScope) resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
    }
}
