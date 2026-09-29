/**
 * Startup diagnostics: Spring Boot failure analyzers that turn a failed start
 * into the setting to fix. Registered in {@code META-INF/spring.factories}, so
 * they apply to every service with common-web on its classpath. Start with
 * {@link com.itways.common.diagnostics.DatabaseLoginFailureAnalyzer}.
 */
package com.itways.common.diagnostics;
