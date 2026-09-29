/**
 * Request correlation on the servlet side (ARC-25): {@link com.itways.web.correlation.RequestIdFilter}
 * gives every request an id (the caller's {@code X-Request-Id} or a new one), echoes it on the
 * response and keeps it in the logging context; {@link com.itways.web.correlation.CurrentRequestId}
 * reads it back for outbound calls and error envelopes; {@link com.itways.web.correlation.RequestCorrelationConfig}
 * registers the filter ({@code @EnableCommon} or {@code @EnableRequestCorrelation}). The rule
 * itself is common-core's {@code com.itways.common.correlation.RequestIds}. Start with the filter.
 */
package com.itways.web.correlation;
