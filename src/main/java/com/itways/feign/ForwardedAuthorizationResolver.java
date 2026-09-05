package com.itways.feign;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * A second place to find the caller's credential when the inbound request
 * carries no {@code Authorization} header.
 *
 * <p>
 * The default forwarding ({@link ForwardedAuthFeignConfig}) copies
 * {@code Authorization} and {@code X-API-KEY} from the current request onto
 * every Feign call. A service whose requests are authenticated some other way
 * — assistant-service's channel webhooks, for instance — registers one bean of
 * this type and the interceptor asks it before giving up.
 */
@FunctionalInterface
public interface ForwardedAuthorizationResolver {

	/** The value to send as {@code Authorization}, or empty when this request has none. */
	Optional<String> resolve(HttpServletRequest request);
}
