package com.itways.security.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.itways.security.servlet.Sessions;
import com.itways.security.servlet.Sessions.CredentialKind;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Who gets a service-call session (D4): a valid token and nothing else. A missing or wrong
 * token, or any tenant credential beside the token, leaves the request as it was; a user who is
 * already signed in stays the user. The comparison is constant-time.
 */
@ExtendWith(OutputCaptureExtension.class)
class ServiceTokenAuthenticationFilterTest {

    private static final String TOKEN = "svc-token-4f1a9c-7d2e";
    private static final String CLAIM = "/api/journeys/internal/knowledge-base/ingestion/claim";

    private final InternalServiceToken internalServiceToken = spy(new InternalServiceToken(TOKEN, true));
    private final ServiceTokenAuthenticationFilter filter = new ServiceTokenAuthenticationFilter(internalServiceToken);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private record Outcome(boolean chainCalled, Authentication authentication, int status) {
    }

    private Outcome run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(chain.getRequest() != null, SecurityContextHolder.getContext().getAuthentication(),
                response.getStatus());
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", CLAIM);
        if (token != null) {
            request.addHeader(InternalServiceToken.HEADER, token);
        }
        return request;
    }

    @Test
    void aValidTokenAloneIsAServiceCall() throws Exception {
        Outcome outcome = run(request(TOKEN));

        assertThat(outcome.chainCalled()).isTrue();
        Authentication auth = outcome.authentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getName()).isEqualTo(Sessions.SERVICE_PRINCIPAL);
        assertThat(AuthorityUtils.authorityListToSet(auth.getAuthorities())).containsExactly(Sessions.AUTHORITY_SERVICE);
        assertThat(auth.getDetails()).isEqualTo(Map.of(Sessions.DETAIL_AUTH_SOURCE, Sessions.AUTH_SOURCE_SERVICE_TOKEN));
        assertThat(Sessions.isServiceCall(auth)).isTrue();
        // Not a person, a key or a webhook: user-only routes refuse it; it names no account.
        assertThat(Sessions.kindOf(auth)).isEqualTo(CredentialKind.NONE);
        assertThat(Sessions.isUserSession(auth)).isFalse();
        assertThat(((Map<?, ?>) auth.getDetails()).get(Sessions.DETAIL_ACCOUNT_ID)).isNull();
    }

    @Test
    void theTokenIsTrimmedAsTheOutboundSideSendsIt() throws Exception {
        assertThat(Sessions.isServiceCall(run(request("  " + TOKEN + "\t")).authentication())).isTrue();
    }

    @Test
    void noTokenLeavesTheRequestAlone() throws Exception {
        Outcome outcome = run(request(null));

        assertThat(outcome.chainCalled()).isTrue();
        assertThat(outcome.authentication()).isNull();
        assertThat(outcome.status()).isEqualTo(200);
    }

    @Test
    void aWrongTokenContinuesUnauthenticatedAndIsLoggedWithoutItsValue(CapturedOutput output) throws Exception {
        String wrong = "not-the-token-3b9e1";
        for (String presented : new String[] { wrong, "", "   ", TOKEN + "x", TOKEN.substring(1), "X" + TOKEN.substring(1),
                TOKEN.substring(0, TOKEN.length() - 1) + "X", TOKEN.toUpperCase() }) {
            SecurityContextHolder.clearContext();
            Outcome outcome = run(request(presented));

            assertThat(outcome.chainCalled()).as(presented).isTrue();
            assertThat(outcome.authentication()).as(presented).isNull();
            // The filter never answers; the chain's entry point does.
            assertThat(outcome.status()).as(presented).isEqualTo(200);
        }
        assertThat(output.getAll()).contains("Service token did not match").doesNotContain(wrong).doesNotContain(TOKEN);
    }

    @Test
    void withoutAConfiguredTokenNothingIsAServiceCall() throws Exception {
        ServiceTokenAuthenticationFilter unconfigured = new ServiceTokenAuthenticationFilter(
                new InternalServiceToken("", false));
        MockFilterChain chain = new MockFilterChain();

        unconfigured.doFilter(request(TOKEN), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void aBearerCredentialBesideTheTokenKeepsTheRequestTheUsers() throws Exception {
        // The Feign interceptor sends the token on every call: a call made for a user carries both.
        // If the user's token is invalid the JWT filter left no session; it must stay that way (401).
        MockHttpServletRequest request = request(TOKEN);
        request.addHeader("Authorization", "Bearer expired.or.forged");

        Outcome outcome = run(request);

        assertThat(outcome.chainCalled()).isTrue();
        assertThat(outcome.authentication()).isNull();
    }

    @Test
    void anApiKeyBesideTheTokenKeepsTheRequestTheKeys() throws Exception {
        MockHttpServletRequest request = request(TOKEN);
        request.addHeader(ServiceTokenAuthenticationFilter.API_KEY_HEADER, "some-api-key");

        assertThat(run(request).authentication()).isNull();
    }

    @Test
    void aUserAlreadySignedInWins() throws Exception {
        UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken(
                new User("user@example.test", "", Collections.emptyList()), null, Collections.emptyList());
        user.setDetails(Map.of(Sessions.DETAIL_ACCOUNT_ID, "4242", Sessions.DETAIL_TOKEN_TYPE, "ACCESS"));
        SecurityContextHolder.getContext().setAuthentication(user);

        Outcome outcome = run(request(TOKEN));

        assertThat(outcome.authentication()).isSameAs(user);
        assertThat(Sessions.isUserSession(outcome.authentication())).isTrue();
        assertThat(Sessions.isServiceCall(outcome.authentication())).isFalse();
    }

    @Test
    void anAnonymousSessionIsReplaced() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(Sessions.isServiceCall(run(request(TOKEN)).authentication())).isTrue();
    }

    @Test
    void theComparisonIsTheConstantTimeOne() throws Exception {
        run(request(TOKEN));

        // InternalServiceToken.matches: trimmed bytes compared with MessageDigest.isEqual.
        verify(internalServiceToken).matches(TOKEN);
    }

    @Test
    void aShortGuessTakesAsLongAsANearlyRightOne() {
        // A short-circuiting comparison (String.equals, Arrays.equals) answers a guess of the wrong
        // length at once and a nearly right one only after reading it all: on a 1 MB token the two
        // differ by orders of magnitude. The constant-time comparison walks the whole configured
        // token either way, so the times stay within a small factor (the nearly right guess also
        // pays for turning 1 MB into bytes). Medians of many runs and a wide bound keep this stable
        // on a busy machine.
        char[] chars = new char[1_000_000];
        Arrays.fill(chars, 'a');
        String secret = new String(chars);
        InternalServiceToken token = new InternalServiceToken(secret, true);
        String shortGuess = "b";
        String nearlyRight = secret.substring(0, secret.length() - 1) + "b";

        for (int i = 0; i < 30; i++) {
            token.matches(shortGuess);
            token.matches(nearlyRight);
        }
        long shortNanos = median(token, shortGuess);
        long nearlyRightNanos = median(token, nearlyRight);

        assertThat(token.matches(shortGuess)).isFalse();
        assertThat(token.matches(nearlyRight)).isFalse();
        double ratio = (double) Math.max(shortNanos, nearlyRightNanos) / Math.max(1, Math.min(shortNanos, nearlyRightNanos));
        assertThat(ratio).as("short guess %d ns, nearly right %d ns", shortNanos, nearlyRightNanos).isLessThan(10.0);
    }

    private static long median(InternalServiceToken token, String presented) {
        long[] samples = new long[41];
        for (int i = 0; i < samples.length; i++) {
            long started = System.nanoTime();
            token.matches(presented);
            samples[i] = System.nanoTime() - started;
        }
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    @Test
    void theServiceCallRuleForAChain() {
        RequestAuthorizationContext context = new RequestAuthorizationContext(new MockHttpServletRequest());

        assertThat(Sessions.serviceCall().check(ServiceTokenAuthenticationFilter::serviceAuthentication, context)
                .isGranted()).isTrue();
        assertThat(Sessions.userSession().check(ServiceTokenAuthenticationFilter::serviceAuthentication, context)
                .isGranted()).isFalse();
        assertThat(Sessions.serviceCall().check(() -> null, context).isGranted()).isFalse();
    }
}
