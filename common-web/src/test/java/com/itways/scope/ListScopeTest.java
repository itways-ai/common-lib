package com.itways.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.itways.common.exception.BusinessException;

class ListScopeTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void theScopeParameterWinsOverTheHeader() {
        assertThat(RequestedScopeArgumentResolver.resolve("shared", A.toString())).isEqualTo(ListScope.SHARED);
        assertThat(RequestedScopeArgumentResolver.resolve("all", A.toString())).isEqualTo(ListScope.ALL);
        assertThat(RequestedScopeArgumentResolver.resolve(B.toString(), A.toString()))
                .isEqualTo(ListScope.assistant(B));
    }

    @Test
    void theHeaderMeansThatAssistantPlusShared() {
        ListScope scope = RequestedScopeArgumentResolver.resolve(null, A.toString());
        assertThat(scope.includes(A)).isTrue();
        assertThat(scope.includes(null)).isTrue();
        assertThat(scope.includes(B)).isFalse();
    }

    @Test
    void nothingMeansTheWholeAccount() {
        assertThat(RequestedScopeArgumentResolver.resolve(null, null)).isEqualTo(ListScope.ALL);
        assertThat(RequestedScopeArgumentResolver.resolve(" ", "")).isEqualTo(ListScope.ALL);
    }

    @Test
    void aMalformedValueIsRefusedNeverWidenedToEverything() {
        assertThatThrownBy(() -> RequestedScopeArgumentResolver.resolve("mine", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ScopeErrors.INVALID_SCOPE));
        assertThatThrownBy(() -> RequestedScopeArgumentResolver.resolve(null, "not-a-uuid"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void queryParametersNeverCarryANull() {
        assertThat(ListScope.ALL.all()).isTrue();
        assertThat(ListScope.ALL.assistantOrNone()).isEqualTo(ListScope.NONE);
        assertThat(ListScope.SHARED.all()).isFalse();
        assertThat(ListScope.SHARED.assistantOrNone()).isEqualTo(ListScope.NONE);
        assertThat(ListScope.assistant(A).assistantOrNone()).isEqualTo(A);
    }

    @Test
    void aSharedRowMayOnlyUseSharedRows() {
        assertThat(AssistantScope.SHARED.mayUse(null)).isTrue();
        assertThat(AssistantScope.SHARED.mayUse(A)).isFalse();
        assertThat(AssistantScope.of(A).mayUse(A)).isTrue();
        assertThat(AssistantScope.of(A).mayUse(null)).isTrue();
        assertThat(AssistantScope.of(A).mayUse(B)).isFalse();
        assertThat(ListScope.usableIn(AssistantScope.SHARED)).isEqualTo(ListScope.SHARED);
        assertThat(ListScope.usableIn(AssistantScope.of(A))).isEqualTo(ListScope.assistant(A));
    }
}
