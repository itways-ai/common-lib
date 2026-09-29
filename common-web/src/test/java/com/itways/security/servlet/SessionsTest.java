package com.itways.security.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import com.itways.security.servlet.Sessions.CredentialKind;

/**
 * The reconciled USER_SESSION rule (ARC-11), every branch: the deny-list of
 * account/channels/journey/template/conversation, auth-service's allow-list on the
 * token type, and conversation-service's name check.
 */
class SessionsTest {

    private static Authentication session(String name, Map<String, String> details) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new User(name, "", Collections.emptyList()), null, Collections.emptyList());
        auth.setDetails(details);
        return auth;
    }

    private static Map<String, String> user(String tokenType) {
        Map<String, String> details = new HashMap<>();
        details.put(Sessions.DETAIL_ACCOUNT_ID, "4242");
        details.put(Sessions.DETAIL_REMOTE_ADDRESS, "127.0.0.1");
        if (tokenType != null) {
            details.put(Sessions.DETAIL_TOKEN_TYPE, tokenType);
        }
        return details;
    }

    private static final Map<String, String> API_KEY = Map.of(Sessions.DETAIL_ACCOUNT_ID, "4242",
            Sessions.DETAIL_KEY_VERSION, "3", Sessions.DETAIL_AUTH_SOURCE, Sessions.AUTH_SOURCE_API_KEY);
    private static final Map<String, String> WEBHOOK = Map.of(Sessions.DETAIL_TOKEN_TYPE, "CHANNEL_WEBHOOK");

    @Test
    void aBearerAccessSessionIsAUser() {
        Authentication auth = session("user@example.test", user("ACCESS"));

        assertThat(Sessions.kindOf(auth)).isEqualTo(CredentialKind.USER);
        assertThat(Sessions.isUserSession(auth)).isTrue();
    }

    @Test
    void aDetailsMapWithoutATokenTypeStillCountsAsAUser() {
        // What many service test fixtures build ({"accountId": ...} only) and the
        // deny-list semantics five services rely on; the JWT filter itself never omits the type.
        Authentication auth = session("user@example.test", user(null));

        assertThat(Sessions.kindOf(auth)).isEqualTo(CredentialKind.USER);
        assertThat(Sessions.isUserSession(session("user@example.test", Map.of()))).isTrue();
    }

    @Test
    void anApiKeyIsNotAUser() {
        Authentication auth = session("user@example.test", API_KEY);

        assertThat(Sessions.kindOf(auth)).isEqualTo(CredentialKind.API_KEY);
        assertThat(Sessions.isUserSession(auth)).isFalse();
    }

    @Test
    void aChannelWebhookTokenIsNotAUser() {
        Authentication auth = session("channel:7", WEBHOOK);

        assertThat(Sessions.kindOf(auth)).isEqualTo(CredentialKind.CHANNEL_WEBHOOK);
        assertThat(Sessions.isUserSession(auth)).isFalse();
    }

    @Test
    void aRefreshOrUnknownTokenTypeIsRefused() {
        // auth-service's allow-list: a token type other than ACCESS is not a person's session.
        for (String type : new String[] { "REFRESH", "SERVICE", "access", "" }) {
            Authentication auth = session("user@example.test", user(type));
            assertThat(Sessions.kindOf(auth)).as(type).isEqualTo(CredentialKind.NONE);
            assertThat(Sessions.isUserSession(auth)).as(type).isFalse();
        }
    }

    @Test
    void aBlankNameIsRefused() {
        // conversation-service's check: a session that names nobody is not a user.
        for (String name : new String[] { "", "   " }) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(name, null,
                    Collections.emptyList());
            auth.setDetails(user("ACCESS"));
            assertThat(Sessions.kindOf(auth)).isEqualTo(CredentialKind.NONE);
            assertThat(Sessions.isUserSession(auth)).isFalse();
        }
        // The kind of credential is still read first: a blank-named API key is an API key.
        UsernamePasswordAuthenticationToken apiKey = new UsernamePasswordAuthenticationToken("", null,
                Collections.emptyList());
        apiKey.setDetails(API_KEY);
        assertThat(Sessions.kindOf(apiKey)).isEqualTo(CredentialKind.API_KEY);
    }

    @Test
    void anonymousUnauthenticatedAndDetailLessSessionsAreNone() {
        assertThat(Sessions.kindOf(null)).isEqualTo(CredentialKind.NONE);
        assertThat(Sessions.isUserSession(null)).isFalse();

        UsernamePasswordAuthenticationToken unauthenticated = new UsernamePasswordAuthenticationToken(
                "user@example.test", null);
        unauthenticated.setDetails(user("ACCESS"));
        assertThat(unauthenticated.isAuthenticated()).isFalse();
        assertThat(Sessions.kindOf(unauthenticated)).isEqualTo(CredentialKind.NONE);

        UsernamePasswordAuthenticationToken noDetails = new UsernamePasswordAuthenticationToken(
                new User("user@example.test", "", Collections.emptyList()), null, Collections.emptyList());
        assertThat(Sessions.kindOf(noDetails)).isEqualTo(CredentialKind.NONE);

        UsernamePasswordAuthenticationToken stringDetails = new UsernamePasswordAuthenticationToken(
                new User("user@example.test", "", Collections.emptyList()), null, Collections.emptyList());
        stringDetails.setDetails("4242");
        assertThat(Sessions.kindOf(stringDetails)).isEqualTo(CredentialKind.NONE);
    }

    @Test
    void theAuthorizationManagerGrantsUsersOnly() {
        RequestAuthorizationContext context = new RequestAuthorizationContext(new MockHttpServletRequest());

        assertThat(Sessions.userSession().check(() -> session("u", user("ACCESS")), context).isGranted()).isTrue();
        assertThat(Sessions.userSession().check(() -> session("u", user(null)), context).isGranted()).isTrue();
        assertThat(Sessions.userSession().check(() -> session("u", API_KEY), context).isGranted()).isFalse();
        assertThat(Sessions.userSession().check(() -> session("u", WEBHOOK), context).isGranted()).isFalse();
        assertThat(Sessions.userSession().check(() -> session("u", user("REFRESH")), context).isGranted()).isFalse();
        assertThat(Sessions.userSession().check(() -> null, context).isGranted()).isFalse();
    }

    @Test
    void theDetailKeysAreTheFiltersOwn() {
        // The literal strings the filters wrote before the constants existed, and services test against.
        assertThat(Sessions.DETAIL_ACCOUNT_ID).isEqualTo("accountId");
        assertThat(Sessions.DETAIL_TOKEN_TYPE).isEqualTo("tokenType");
        assertThat(Sessions.DETAIL_AUTH_SOURCE).isEqualTo("authSource");
        assertThat(Sessions.AUTH_SOURCE_API_KEY).isEqualTo("API_KEY");
        assertThat(Sessions.DETAIL_KEY_VERSION).isEqualTo("keyVersion");
        assertThat(Sessions.DETAIL_REMOTE_ADDRESS).isEqualTo("remoteAddress");
    }
}
