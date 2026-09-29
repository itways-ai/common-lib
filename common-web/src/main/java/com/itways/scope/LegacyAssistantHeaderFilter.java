package com.itways.scope;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts the legacy assistant header during the rename (2.1.0): a request that
 * sends only {@link ScopeHeaders#LEGACY_ASSISTANT} reaches the service as if it had
 * sent {@link ScopeHeaders#ASSISTANT} with the same value. Controllers, the
 * {@code @RequestedScope} resolver and anything else that reads
 * {@link ScopeHeaders#ASSISTANT} therefore accept both names without knowing about
 * the old one. When both are sent, {@link ScopeHeaders#ASSISTANT} wins and the
 * request is left untouched.
 *
 * <p>
 * Registered by {@link LegacyAssistantHeaderConfig}. Removed with the legacy name.
 */
public class LegacyAssistantHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getHeader(ScopeHeaders.ASSISTANT) != null) {
            filterChain.doFilter(request, response);
            return;
        }
        String legacy = AssistantHeader.read(request::getHeader);
        filterChain.doFilter(legacy == null ? request : new WithAssistantHeader(request, legacy), response);
    }

    /** The request with {@link ScopeHeaders#ASSISTANT} added; every other header as sent. */
    static final class WithAssistantHeader extends HttpServletRequestWrapper {

        private final String assistant;

        WithAssistantHeader(HttpServletRequest request, String assistant) {
            super(request);
            this.assistant = assistant;
        }

        @Override
        public String getHeader(String name) {
            return ScopeHeaders.ASSISTANT.equalsIgnoreCase(name) ? assistant : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return ScopeHeaders.ASSISTANT.equalsIgnoreCase(name) ? Collections.enumeration(List.of(assistant))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> sent = super.getHeaderNames();
            if (sent != null) {
                names.addAll(Collections.list(sent));
            }
            names.add(ScopeHeaders.ASSISTANT);
            return Collections.enumeration(names);
        }
    }
}
