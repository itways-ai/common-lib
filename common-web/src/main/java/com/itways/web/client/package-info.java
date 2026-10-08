/**
 * Calls from one platform service to another (ARC-11): {@link com.itways.web.client.ServiceCalls}
 * builds the {@code RestClient}, {@link com.itways.web.client.ForwardedCallerInterceptor}
 * puts the caller's credential on every request, and
 * {@link com.itways.web.client.CallerCredentials} is the resolution both share with
 * the Feign interceptor of {@code @EnableForwardedAuth}. Start with {@code ServiceCalls}.
 */
package com.itways.web.client;
