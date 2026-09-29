package com.itways.web.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import com.itways.common.correlation.RequestIds;
import com.itways.common.response.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

class CurrentRequestIdTest {

    @AfterEach
    void cleanThread() {
        MDC.clear();
    }

    @Test
    void readsTheLoggingContext() {
        assertThat(CurrentRequestId.get()).isEmpty();

        MDC.put(RequestIds.MDC_KEY, "  ");
        assertThat(CurrentRequestId.get()).isEmpty();

        MDC.put(RequestIds.MDC_KEY, "req-1");
        assertThat(CurrentRequestId.get()).contains("req-1");
    }

    @Test
    void fallsBackToTheRequestAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(CurrentRequestId.of(request)).isEmpty();
        assertThat(CurrentRequestId.of(null)).isEmpty();

        request.setAttribute(RequestIds.REQUEST_ATTRIBUTE, "from-attribute");
        assertThat(CurrentRequestId.of(request)).contains("from-attribute");

        request.setAttribute(RequestIds.REQUEST_ATTRIBUTE, "not well formed");
        assertThat(CurrentRequestId.of(request)).isEmpty();

        MDC.put(RequestIds.MDC_KEY, "from-mdc");
        assertThat(CurrentRequestId.of(request)).contains("from-mdc");
    }

    @Test
    void stampSetsTheReferenceOnlyWhenThereIsAnIdAndNoReferenceYet() {
        assertThat(CurrentRequestId.stamp(ApiResponse.error("x", "X")).getReference()).isNull();

        MDC.put(RequestIds.MDC_KEY, "req-2");
        assertThat(CurrentRequestId.stamp(ApiResponse.error("x", "X")).getReference()).isEqualTo("req-2");

        ApiResponse<Void> own = ApiResponse.error("x", "X");
        own.setReference("own");
        assertThat(CurrentRequestId.stamp(own).getReference()).isEqualTo("own");
        assertThat(CurrentRequestId.stamp((ApiResponse<Void>) null)).isNull();
    }

    @Test
    void stampWithARequestUsesItsAttributeWhenTheContextIsEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIds.REQUEST_ATTRIBUTE, "req-3");

        assertThat(CurrentRequestId.stamp(ApiResponse.error("x", "X"), request).getReference()).isEqualTo("req-3");
    }
}
