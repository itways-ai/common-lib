package com.itways.feign;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.itways.common.correlation.RequestIds;
import com.itways.security.internal.InternalServiceToken;
import com.itways.web.client.CallerCredentials;
import com.itways.web.client.ServiceCalls;
import com.itways.web.correlation.CurrentRequestId;

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
 * registering a {@link ForwardedAuthorizationResolver}. The resolution is
 * {@link CallerCredentials}, the same one the {@code RestClient} side
 * ({@link ServiceCalls}) uses.
 *
 * <p>
 * When {@code itways.internal-token} is set, every call also carries it as
 * {@code X-Service-Token}, so the service called can tell a platform service
 * from any other caller on its internal routes ({@link InternalServiceToken}).
 * It is sent even without a current request (scheduled jobs, consumers).
 *
 * <p>
 * The current request id goes along as {@code X-Request-Id} (ARC-25;
 * {@link CurrentRequestId}, so also from a message listener), unless the
 * call already names one.
 *
 * <p>
 * Opt in with {@code @EnableForwardedAuth}; the class is not auto-configured,
 * because a service without Feign has no {@code RequestInterceptor} to give
 * (Feign is an optional dependency of this module).
 */
@Configuration
@ConditionalOnClass(name = "feign.RequestInterceptor")
@Slf4j
public class ForwardedAuthFeignConfig {

	@Bean
	public RequestInterceptor forwardedAuthRequestInterceptor(ObjectProvider<ForwardedAuthorizationResolver> fallback,
			@Value("${itways.internal-token:}") String internalToken) {
		String serviceToken = internalToken == null ? "" : internalToken.trim();
		return template -> {
			if (!serviceToken.isEmpty()) {
				template.header(InternalServiceToken.HEADER, serviceToken);
			}
			if (!template.headers().containsKey(RequestIds.HEADER)) {
				CurrentRequestId.get().ifPresent(id -> template.header(RequestIds.HEADER, id));
			}
			HttpServletRequest request = CallerCredentials.currentRequest();
			if (request == null) {
				return;
			}
			CallerCredentials credentials = CallerCredentials.of(request, fallback.getIfAvailable());

			if (credentials.authorization() != null) {
				template.header("Authorization", credentials.authorization());
				log.debug("Forwarding Authorization header");
			}
			if (credentials.apiKey() != null) {
				template.header(ServiceCalls.API_KEY_HEADER, credentials.apiKey());
				log.debug("Forwarding X-API-KEY header");
			}

			if (credentials.isEmpty()) {
				log.warn("No authentication headers found in current request to forward");
			}
		};
	}
}
