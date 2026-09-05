package com.itways.feign;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Forwards the caller's credential on every Feign call a service makes.
 *
 * <p>
 * A request that reached this service carried either a bearer token
 * ({@code Authorization}) or an account API key ({@code X-API-KEY}); the
 * service it calls next needs the same one to know who is asking. Both
 * headers are copied when present. A service can add one more source by
 * registering a {@link ForwardedAuthorizationResolver}.
 *
 * <p>
 * Opt in with {@code @EnableForwardedAuth}; the class is not auto-configured,
 * because a service without Feign has no {@code RequestInterceptor} to give.
 */
@Configuration
@Slf4j
public class ForwardedAuthFeignConfig {

	@Bean
	public RequestInterceptor forwardedAuthRequestInterceptor(ObjectProvider<ForwardedAuthorizationResolver> fallback) {
		return template -> {
			ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
			if (attributes == null || attributes.getRequest() == null) {
				return;
			}
			HttpServletRequest request = attributes.getRequest();

			String authorization = request.getHeader("Authorization");
			if (authorization == null) {
				ForwardedAuthorizationResolver resolver = fallback.getIfAvailable();
				if (resolver != null) {
					authorization = resolver.resolve(request).orElse(null);
				}
			}
			if (authorization != null) {
				template.header("Authorization", authorization);
				log.debug("Forwarding Authorization header");
			}

			String apiKey = request.getHeader("X-API-KEY");
			if (apiKey != null) {
				template.header("X-API-KEY", apiKey);
				log.debug("Forwarding X-API-KEY header");
			}

			if (authorization == null && apiKey == null) {
				log.warn("No authentication headers found in current request to forward");
			}
		};
	}
}
