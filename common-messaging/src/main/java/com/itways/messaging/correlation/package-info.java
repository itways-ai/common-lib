/**
 * Request correlation over RabbitMQ (ARC-25): {@link com.itways.messaging.correlation.RequestIdPublishPostProcessor}
 * puts the request id of the logging context on every published message as {@code x-request-id};
 * {@link com.itways.messaging.correlation.RequestIdListenerAdvice} puts it back in the logging
 * context while a listener handles the message, and
 * {@link com.itways.messaging.correlation.RequestIdListenerAdviceRegistrar} adds that advice to every
 * listener container factory. All three are wired by {@code RabbitPublishingAutoConfiguration}
 * ({@code itways.request-id.messaging.enabled}, default {@code true}). The rule itself is
 * common-core's {@code com.itways.common.correlation.RequestIds}.
 */
package com.itways.messaging.correlation;
