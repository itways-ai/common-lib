package com.itways.security.servlet;

import com.itways.security.core.TokenVerifier;
import java.util.Map;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * What kind of credential the current session was built from, read from the
 * details map the platform's authentication filters set (ARC-11; the one
 * version of the {@code USER_SESSION} rule account, channels, journey, template
 * and conversation-service each carried).
 *
 * <p>
 * {@code JwtAuthenticationFilter} puts {@value #DETAIL_ACCOUNT_ID},
 * {@value #DETAIL_TOKEN_TYPE} ({@code ACCESS} or {@code CHANNEL_WEBHOOK}) and
 * {@value #DETAIL_REMOTE_ADDRESS} on a bearer session;
 * {@code ApiKeyAuthenticationFilter} puts {@value #DETAIL_ACCOUNT_ID},
 * {@value #DETAIL_KEY_VERSION} and {@value #DETAIL_AUTH_SOURCE} =
 * {@value #AUTH_SOURCE_API_KEY} on an API-key session. A service that lets only
 * a signed-in person manage things uses {@link #userSession()} in its chain
 * ({@code anyRequest().access(Sessions.userSession())}) or
 * {@link #isUserSession} in code, and keeps its own 403 wording.
 *
 * <p>
 * The user rule, reconciled from the six copies: the session is authenticated,
 * names a principal, has a details map, is not an API key
 * ({@code authSource}), is not a channel webhook token ({@code tokenType}), and
 * if it names a token type at all that type is {@code ACCESS}. The last clause
 * is auth-service's allow-list, applied to every real token the JWT filter
 * emits (a refresh token, or a type nobody has minted yet, is not a user); a
 * details map <em>without</em> a token type still counts as a user, because the
 * other five services and many of their test fixtures build one that way, and
 * the filter itself never omits it. A blank principal name is refused, as
 * conversation-service did.
 *
 * <p>
 * A <em>service call</em> (2.2.0) is another platform service calling with the
 * shared service token and no tenant credential, as a background worker does
 * ({@code ServiceTokenAuthenticationFilter}: authority {@value #AUTHORITY_SERVICE},
 * {@value #DETAIL_AUTH_SOURCE} = {@value #AUTH_SOURCE_SERVICE_TOKEN}, no account).
 * It is none of the credential kinds ({@link #kindOf} says {@link CredentialKind#NONE},
 * so {@link #userSession()} refuses it) and is admitted only where a chain says
 * {@link #serviceCall()}. Note that {@code authenticated()} admits it too.
 */
public final class Sessions {

    /** Details key: the account the session belongs to (both filters). */
    public static final String DETAIL_ACCOUNT_ID = "accountId";
    /** Details key: the JWT's {@code type} claim, {@code ACCESS} when absent (JWT filter only). */
    public static final String DETAIL_TOKEN_TYPE = "tokenType";
    /**
     * Details key: how the caller authenticated; {@value #AUTH_SOURCE_API_KEY} or (2.2.0)
     * {@value #AUTH_SOURCE_SERVICE_TOKEN}, absent on a bearer session.
     */
    public static final String DETAIL_AUTH_SOURCE = "authSource";
    /** {@value #DETAIL_AUTH_SOURCE} value of an API-key session. */
    public static final String AUTH_SOURCE_API_KEY = "API_KEY";
    /** Details key: the API key's version (API-key filter only). */
    public static final String DETAIL_KEY_VERSION = "keyVersion";
    /** Details key: the socket address the request came from (JWT filter only). */
    public static final String DETAIL_REMOTE_ADDRESS = "remoteAddress";
    /** {@value #DETAIL_AUTH_SOURCE} value of a service call (2.2.0, service-token filter). */
    public static final String AUTH_SOURCE_SERVICE_TOKEN = "SERVICE_TOKEN";
    /** The authority of a service call (2.2.0): {@code hasAuthority("SERVICE")}. */
    public static final String AUTHORITY_SERVICE = "SERVICE";
    /** The principal name of a service call (2.2.0). */
    public static final String SERVICE_PRINCIPAL = "platform-service";

    /** The kinds of credential a session is built from. */
    public enum CredentialKind {
        /** A signed-in person's access token. */
        USER,
        /** An account API key ({@code X-API-KEY}). */
        API_KEY,
        /** A channel's webhook token, embedded in a provider's webhook URL. */
        CHANNEL_WEBHOOK,
        /**
         * Anonymous, a service call ({@link Sessions#isServiceCall}), or a session this class
         * does not recognise as any of the above.
         */
        NONE
    }

    private Sessions() {
    }

    /** The kind of credential behind {@code auth}; {@link CredentialKind#NONE} for null or anonymous. */
    public static CredentialKind kindOf(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || !(auth.getDetails() instanceof Map<?, ?> details)) {
            return CredentialKind.NONE;
        }
        if (AUTH_SOURCE_API_KEY.equals(details.get(DETAIL_AUTH_SOURCE))) {
            return CredentialKind.API_KEY;
        }
        if (AUTH_SOURCE_SERVICE_TOKEN.equals(details.get(DETAIL_AUTH_SOURCE))) {
            // A platform service, not a person, a key or a webhook: see isServiceCall.
            return CredentialKind.NONE;
        }
        Object tokenType = details.get(DETAIL_TOKEN_TYPE);
        if (TokenVerifier.TYPE_CHANNEL_WEBHOOK.equals(tokenType)) {
            return CredentialKind.CHANNEL_WEBHOOK;
        }
        if (auth.getName() == null || auth.getName().isBlank()) {
            return CredentialKind.NONE;
        }
        if (tokenType != null && !TokenVerifier.TYPE_ACCESS.equals(tokenType)) {
            // A refresh token, or a type nobody mints yet: valid, but not a person's session.
            return CredentialKind.NONE;
        }
        return CredentialKind.USER;
    }

    /** True for a signed-in person's session; false for API keys, webhook tokens and anonymous callers. */
    public static boolean isUserSession(Authentication auth) {
        return kindOf(auth) == CredentialKind.USER;
    }

    /** For a chain: {@code anyRequest().access(Sessions.userSession())}. */
    public static AuthorizationManager<RequestAuthorizationContext> userSession() {
        return (authentication, context) -> new AuthorizationDecision(isUserSession(authentication.get()));
    }

    /**
     * True for a service call (2.2.0): authenticated, {@value #DETAIL_AUTH_SOURCE} =
     * {@value #AUTH_SOURCE_SERVICE_TOKEN} and the {@value #AUTHORITY_SERVICE} authority, as
     * {@code ServiceTokenAuthenticationFilter} sets them. False for everything else, a user's
     * session that also carried the token included (the user wins there).
     */
    public static boolean isServiceCall(Authentication auth) {
        return auth != null && auth.isAuthenticated() && auth.getDetails() instanceof Map<?, ?> details
                && AUTH_SOURCE_SERVICE_TOKEN.equals(details.get(DETAIL_AUTH_SOURCE))
                && auth.getAuthorities().stream().anyMatch(a -> AUTHORITY_SERVICE.equals(a.getAuthority()));
    }

    /**
     * For a chain (2.2.0): routes only another platform service may call, e.g.
     * {@code requestMatchers(".../ingestion/**").access(Sessions.serviceCall())}. Needs the
     * service to put {@code ServiceTokenAuthenticationFilter} in its chain; without it (or
     * without {@code itways.internal-token}) nothing is admitted.
     */
    public static AuthorizationManager<RequestAuthorizationContext> serviceCall() {
        return (authentication, context) -> new AuthorizationDecision(isServiceCall(authentication.get()));
    }
}
