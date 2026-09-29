/**
 * Calls to addresses a tenant or a provider supplies (ARC-11, from
 * speech-service): DNS pinned to vetted public addresses
 * ({@link com.itways.web.net.PublicOnlyDnsResolver},
 * {@link com.itways.web.net.PinnedHttpClients}; SPC-03) and downloads read as
 * a bounded stream ({@link com.itways.web.net.BoundedDownloads}; F02). Needs
 * Apache HttpClient 5 on the service's classpath (an optional dependency of
 * common-web); {@code BoundedDownloads} needs only spring-web. Start with
 * {@code PinnedHttpClients}.
 */
package com.itways.web.net;
