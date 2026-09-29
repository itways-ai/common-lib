package com.itways.security.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

/** The strict check and the outbound value added for ARC-11, beside the unchanged rollout rule. */
@ExtendWith(OutputCaptureExtension.class)
class InternalServiceTokenTest {

    private static final String TOKEN = "svc-token-4f1a9c";

    @Test
    void matchesIsStrictWhateverTheEnforceFlag(CapturedOutput output) {
        for (boolean enforce : new boolean[] { false, true }) {
            InternalServiceToken token = new InternalServiceToken(" " + TOKEN + " ", enforce);

            assertThat(token.matches(TOKEN)).isTrue();
            assertThat(token.matches("  " + TOKEN + "\t")).isTrue();
            assertThat(token.matches("wrong")).isFalse();
            assertThat(token.matches(TOKEN + "x")).isFalse();
            assertThat(token.matches(null)).isFalse();
            assertThat(token.matches("")).isFalse();
            assertThat(token.matches("   ")).isFalse();
        }
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    void withoutAConfiguredTokenNothingMatches() {
        InternalServiceToken none = new InternalServiceToken("", false);
        InternalServiceToken blank = new InternalServiceToken("   ", false);
        InternalServiceToken absent = new InternalServiceToken(null, false);

        for (InternalServiceToken token : new InternalServiceToken[] { none, blank, absent }) {
            assertThat(token.isConfigured()).isFalse();
            assertThat(token.headerValue()).isNull();
            assertThat(token.matches("")).isFalse();
            assertThat(token.matches("anything")).isFalse();
            // The rollout rule is unchanged: no token and not enforced admits everything.
            assertThat(token.admits(new MockHttpServletRequest("GET", "/api/x/internal/y"))).isTrue();
        }
    }

    @Test
    void theOutboundValueIsTheTrimmedToken() {
        InternalServiceToken token = new InternalServiceToken("\t" + TOKEN + " \n", true);

        assertThat(token.isConfigured()).isTrue();
        assertThat(token.headerValue()).isEqualTo(TOKEN);
    }

    @Test
    void admitsKeepsTheRolloutRule(CapturedOutput output) {
        MockHttpServletRequest without = new MockHttpServletRequest("GET", "/api/x/internal/y");
        MockHttpServletRequest with = new MockHttpServletRequest("GET", "/api/x/internal/y");
        with.addHeader(InternalServiceToken.HEADER, TOKEN);

        assertThat(new InternalServiceToken(TOKEN, false).admits(without)).isTrue();
        assertThat(new InternalServiceToken(TOKEN, true).admits(without)).isFalse();
        assertThat(new InternalServiceToken(TOKEN, true).admits(with)).isTrue();
        assertThat(new InternalServiceToken("", true).admits(with)).isFalse();
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }
}
