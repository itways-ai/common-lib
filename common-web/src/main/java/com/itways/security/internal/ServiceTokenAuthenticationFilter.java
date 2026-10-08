package com.itways.security.internal;

import com.itways.security.servlet.Sessions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives a call from another platform service that carries no tenant credential a session of its
 * own (2.2.0, decision D4 of the knowledge-base upgrade): a background worker has no user's token
 * to forward, and forwarding an expiring one into background work would be wrong.
 *
 * <p>
 * A request gets a service-call session ({@link Sessions#isServiceCall}: authority
 * {@value Sessions#AUTHORITY_SERVICE}, principal {@value Sessions#SERVICE_PRINCIPAL}, no account)
 * when all of these hold:
 * <ul>
 * <li>it carries {@value InternalServiceToken#HEADER}, and the value is the configured
 * {@code itways.internal-token} ({@link InternalServiceToken#matches}: trimmed, compared in
 * constant time, never logged);</li>
 * <li>it carries no tenant credential: no {@code Authorization} and no {@code X-API-KEY} header.
 * The platform's Feign interceptor sends the token on every call, so a call made while serving a
 * user carries both; it stays that user's call, and when the user's credential is invalid it
 * stays unauthenticated (401 as before), never a service call;</li>
 * <li>no filter before it authenticated the request already (the user wins in either order: the
 * JWT and API-key filters overwrite whatever they find).</li>
 * </ul>
 * A wrong token is logged (method and path only) and the request continues unauthenticated, so
 * the chain's entry point answers. The filter never answers a request itself.
 *
 * <p>
 * It establishes who the caller is and decides nothing: a service admits service calls where its
 * chain says {@code access(Sessions.serviceCall())}. It runs only where a service puts it in its
 * {@code SecurityFilterChain}, after the JWT and API-key filters, e.g.
 * {@code .addFilterBefore(serviceTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)}
 * written after their two lines. {@link ServiceTokenAuthenticationConfig} registers it as a bean
 * (with {@code @EnableCustomSecurity}, when {@code itways.internal-token} is set) and keeps it out
 * of the servlet container's own filter chain.
 */
@Slf4j
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    /** The header an account API key comes in (as {@code ApiKeyAuthenticationFilter} reads it). */
    static final String API_KEY_HEADER = "X-API-KEY";

    private final InternalServiceToken internalServiceToken;

    public ServiceTokenAuthenticationFilter(InternalServiceToken internalServiceToken) {
        this.internalServiceToken = internalServiceToken;
    }

    /**
     * The session a service call gets: authenticated, principal {@value Sessions#SERVICE_PRINCIPAL},
     * authority {@value Sessions#AUTHORITY_SERVICE}, details
     * {@code {authSource: SERVICE_TOKEN}}. Public so a service's security tests can build one.
     */
    public static Authentication serviceAuthentication() {
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(Sessions.AUTHORITY_SERVICE));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                new User(Sessions.SERVICE_PRINCIPAL, "", authorities), null, authorities);
        authentication.setDetails(Map.of(Sessions.DETAIL_AUTH_SOURCE, Sessions.AUTH_SOURCE_SERVICE_TOKEN));
        return authentication;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String presented = request.getHeader(InternalServiceToken.HEADER);
        if (presented != null && !carriesTenantCredential(request) && !alreadyAuthenticated()) {
            if (internalServiceToken.matches(presented)) {
                SecurityContextHolder.getContext().setAuthentication(serviceAuthentication());
            } else {
                log.warn("Service token did not match; continuing unauthenticated: {} {}", request.getMethod(),
                        request.getRequestURI());
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean carriesTenantCredential(HttpServletRequest request) {
        return StringUtils.hasText(request.getHeader("Authorization"))
                || StringUtils.hasText(request.getHeader(API_KEY_HEADER));
    }

    private static boolean alreadyAuthenticated() {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        return existing != null && existing.isAuthenticated() && !(existing instanceof AnonymousAuthenticationToken);
    }
}
