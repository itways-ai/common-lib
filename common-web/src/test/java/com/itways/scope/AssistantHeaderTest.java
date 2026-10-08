package com.itways.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.itways.common.exception.BusinessException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The selected scope read from the assistant header: an assistant, the Shared workspace, or nothing. */
@SuppressWarnings("removal") // the legacy header name is what some of these tests send
class AssistantHeaderTest {

    private static final UUID SELECTED = UUID.fromString("5f0c1a4e-0000-4000-8000-000000000001");

    @Test
    void theSharedValueIsShared() {
        assertThat(ScopeHeaders.SHARED_VALUE).isEqualTo("shared");
    }

    @Test
    void anAssistantIdSelectsThatAssistant() {
        SelectedScope read = AssistantHeader.read(Map.of(ScopeHeaders.ASSISTANT, SELECTED.toString())::get);

        assertThat(read).isEqualTo(SelectedScope.assistant(SELECTED));
        assertThat(read.kind()).isEqualTo(SelectedScope.Kind.ASSISTANT);
        assertThat(read.shared()).isFalse();
        assertThat(read.none()).isFalse();
        assertThat(read.asListScope()).isEqualTo(ListScope.assistant(SELECTED));
    }

    @Test
    void theSharedSentinelSelectsTheSharedWorkspaceInAnyCase() {
        for (String value : new String[] { "shared", "SHARED", " Shared " }) {
            SelectedScope read = AssistantHeader.read(Map.of(ScopeHeaders.ASSISTANT, value)::get);

            assertThat(read).isSameAs(SelectedScope.SHARED);
            assertThat(read.shared()).isTrue();
            assertThat(read.assistantId()).isNull();
            assertThat(read.asListScope()).isEqualTo(ListScope.SHARED);
        }
    }

    @Test
    void noHeaderIsNone() {
        SelectedScope read = AssistantHeader.read(name -> null);

        assertThat(read).isSameAs(SelectedScope.NONE);
        assertThat(read.none()).isTrue();
        assertThat(read.asListScope()).isEqualTo(ListScope.ALL);
        assertThat(AssistantHeader.read(Map.of(ScopeHeaders.ASSISTANT, "  ")::get)).isSameAs(SelectedScope.NONE);
        assertThat(AssistantHeader.raw(name -> null)).isNull();
    }

    @Test
    void theLegacyHeaderIsReadWhenTheNewOneIsAbsent() {
        assertThat(AssistantHeader.read(Map.of(ScopeHeaders.LEGACY_ASSISTANT, "shared")::get))
                .isSameAs(SelectedScope.SHARED);
        assertThat(AssistantHeader.read(Map.of(ScopeHeaders.LEGACY_ASSISTANT, SELECTED.toString())::get))
                .isEqualTo(SelectedScope.assistant(SELECTED));
        assertThat(AssistantHeader.read(
                Map.of(ScopeHeaders.ASSISTANT, SELECTED.toString(), ScopeHeaders.LEGACY_ASSISTANT, "shared")::get))
                .isEqualTo(SelectedScope.assistant(SELECTED));
    }

    @Test
    void anythingElseIsA400() {
        assertThatThrownBy(() -> AssistantHeader.read(Map.of(ScopeHeaders.ASSISTANT, "all")::get))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                    assertThat(e.getErrorCode()).isEqualTo(ScopeErrors.INVALID_SCOPE);
                });
        assertThatThrownBy(() -> SelectedScope.parse("not-a-uuid")).isInstanceOf(BusinessException.class)
                .hasMessageContaining("not-a-uuid");
    }

    @Test
    void rawNeverParses() {
        assertThat(AssistantHeader.raw(Map.of(ScopeHeaders.ASSISTANT, "not-a-uuid")::get)).isEqualTo("not-a-uuid");
        assertThat(AssistantHeader.raw(Map.of(ScopeHeaders.LEGACY_ASSISTANT, "shared")::get)).isEqualTo("shared");
    }

    @Test
    void ofANullUuidIsNoneLikeTheOldHeaderParameter() {
        assertThat(SelectedScope.of(null)).isSameAs(SelectedScope.NONE);
        assertThat(SelectedScope.of(SELECTED)).isEqualTo(SelectedScope.assistant(SELECTED));
        assertThatThrownBy(() -> new SelectedScope(SelectedScope.Kind.SHARED, SELECTED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SelectedScope(SelectedScope.Kind.ASSISTANT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
