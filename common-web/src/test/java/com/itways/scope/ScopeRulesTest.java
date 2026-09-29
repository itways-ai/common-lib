package com.itways.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.itways.common.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ScopeRulesTest {

    private static final String ACCOUNT = "acc-1";
    private static final UUID MINE = UUID.randomUUID();
    private static final UUID OTHER_ACCOUNTS = UUID.randomUUID();

    private final AssistantDirectory directory = mock(AssistantDirectory.class);
    private final ScopeRules rules = new ScopeRules(directory, mock(JdbcTemplate.class));

    {
        when(directory.belongsTo(MINE, ACCOUNT)).thenReturn(true);
        when(directory.belongsTo(OTHER_ACCOUNTS, ACCOUNT)).thenReturn(false);
    }

    @Test
    void sharedIsExplicitAndWinsOverAnyAssistant() {
        assertThat(rules.forCreate(MINE, true, MINE, ACCOUNT)).isEqualTo(AssistantScope.SHARED);
        verify(directory, never()).belongsTo(any(), any());
    }

    @Test
    void theRequestedAssistantWinsOverTheSelectedOne() {
        UUID selected = UUID.randomUUID();
        assertThat(rules.forCreate(MINE, null, selected, ACCOUNT).assistantId()).isEqualTo(MINE);
    }

    @Test
    void theSelectedAssistantIsTheDefault() {
        assertThat(rules.forCreate(null, false, MINE, ACCOUNT).assistantId()).isEqualTo(MINE);
    }

    @Test
    void aNewRowWithNoOwnerIsRefusedRatherThanSilentlyShared() {
        assertThatThrownBy(() -> rules.forCreate(null, null, null, ACCOUNT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                    assertThat(e.getErrorCode()).isEqualTo(ScopeErrors.SCOPE_REQUIRED);
                });
    }

    @Test
    void anotherAccountsAssistantIsNotFound() {
        assertThatThrownBy(() -> rules.forCreate(OTHER_ACCOUNTS, null, null, ACCOUNT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(404);
                    assertThat(e.getErrorCode()).isEqualTo(ScopeErrors.ASSISTANT_NOT_FOUND);
                });
        assertThatThrownBy(() -> rules.forCreate(null, null, OTHER_ACCOUNTS, ACCOUNT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void anOwnerOnlyRowCannotBeShared() {
        assertThatThrownBy(() -> rules.ownerForCreate(MINE, true, null, ACCOUNT, "A channel"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ScopeErrors.SHARED_NOT_ALLOWED));
        assertThat(rules.ownerForCreate(null, null, MINE, ACCOUNT, "A channel")).isEqualTo(MINE);
    }

    @Test
    void anOmittedAssistantKeepsTheScopeOnUpdate() {
        AssistantScope current = AssistantScope.of(MINE);
        assertThat(rules.forUpdate(null, null, current, ACCOUNT)).isEqualTo(current);
        assertThat(rules.forUpdate(null, false, AssistantScope.SHARED, ACCOUNT)).isEqualTo(AssistantScope.SHARED);
        assertThat(rules.forUpdate(null, true, current, ACCOUNT)).isEqualTo(AssistantScope.SHARED);
    }

    @Test
    void movingToAnotherAccountsAssistantIsNotFound() {
        assertThatThrownBy(() -> rules.forUpdate(OTHER_ACCOUNTS, null, AssistantScope.SHARED, ACCOUNT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resendingTheCurrentAssistantDoesNotLookItUpAgain() {
        rules.forUpdate(MINE, null, AssistantScope.of(MINE), ACCOUNT);
        verify(directory, never()).belongsTo(any(), any());
    }
}
