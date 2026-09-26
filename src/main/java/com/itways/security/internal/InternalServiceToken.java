package com.itways.security.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Tells a call from another platform service apart from any other caller on an
 * {@code /internal/} route.
 *
 * <p>
 * Internal routes used to trust whatever tenant credential the calling service
 * forwarded, and told internal calls from public ones only by the absence of
 * proxy headers — so anyone who reached a service port without passing the
 * gateway looked internal. Every service now shares one secret,
 * {@code itways.internal-token}; the Feign interceptor of
 * {@code @EnableForwardedAuth} sends it as {@value #HEADER} on every call, and a
 * service checks it here before serving an internal route. The tenant
 * credential is still forwarded and checked as before: the token says which
 * <em>caller</em> this is, the credential says for which account.
 *
 * <p>
 * Rolled out in two steps, so nothing breaks while callers catch up:
 * <ul>
 * <li>{@code itways.internal-token-enforce=false} (default): a missing or wrong
 * token is logged and let through;</li>
 * <li>{@code =true}: it is refused. With enforcement on and no token configured,
 * every internal call is refused (fail closed).</li>
 * </ul>
 * With no token configured and enforcement off, this admits everything, which is
 * the behaviour before this class existed.
 */
@Slf4j
@Component
public class InternalServiceToken {

    public static final String HEADER = "X-Service-Token";

    private final byte[] token;
    private final boolean enforce;

    public InternalServiceToken(@Value("${itways.internal-token:}") String token,
            @Value("${itways.internal-token-enforce:false}") boolean enforce) {
        this.token = token == null ? new byte[0] : token.trim().getBytes(StandardCharsets.UTF_8);
        this.enforce = enforce;
        if (enforce && this.token.length == 0) {
            log.error("itways.internal-token-enforce=true but no itways.internal-token is set: "
                    + "every internal call will be refused");
        } else if (this.token.length > 0) {
            log.info("Internal service token configured ({})", enforce ? "enforced" : "log only");
        }
    }

    /** Whether {@code request} may use an internal route; logs every call without a valid token. */
    public boolean admits(HttpServletRequest request) {
        if (token.length == 0) {
            return !enforce;
        }
        String presented = request.getHeader(HEADER);
        if (presented != null && MessageDigest.isEqual(token, presented.trim().getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        String problem = presented == null ? "no" : "a wrong";
        if (enforce) {
            log.warn("Refused internal call with {} service token: {} {}", problem, request.getMethod(),
                    request.getRequestURI());
            return false;
        }
        log.warn("Internal call with {} service token (allowed; refused once itways.internal-token-enforce=true): {} {}",
                problem, request.getMethod(), request.getRequestURI());
        return true;
    }
}
