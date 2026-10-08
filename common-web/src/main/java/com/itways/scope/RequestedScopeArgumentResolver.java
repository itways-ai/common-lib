package com.itways.scope;

import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link RequestedScope} parameters; see there for the precedence. The
 * header is {@link ScopeHeaders#ASSISTANT}, else the legacy
 * {@link ScopeHeaders#LEGACY_ASSISTANT} (2.1.0 transition). A header of
 * {@link ScopeHeaders#SHARED_VALUE} (the Shared workspace, 2.4.0) lists the
 * shared rows only, like {@code scope=shared}.
 */
public class RequestedScopeArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestedScope.class)
                && ListScope.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return resolve(webRequest.getParameter(ScopeHeaders.SCOPE_PARAM), AssistantHeader.raw(webRequest::getHeader));
    }

    /**
     * The list a request asked for; malformed values are a 400, never "everything".
     * With the header already read as a {@link SelectedScope}, use
     * {@link SelectedScope#asListScope()} when no {@code scope} parameter is sent.
     */
    public static ListScope resolve(String scopeParam, String assistantHeader) {
        ListScope fromParam = fromParam(scopeParam);
        return fromParam != null ? fromParam : SelectedScope.parse(assistantHeader).asListScope();
    }

    /** The {@code scope} parameter's list, or null when it is not sent. */
    private static ListScope fromParam(String scopeParam) {
        if (scopeParam == null || scopeParam.isBlank()) {
            return null;
        }
        String value = scopeParam.trim();
        if ("all".equalsIgnoreCase(value)) {
            return ListScope.ALL;
        }
        if (ScopeHeaders.SHARED_VALUE.equalsIgnoreCase(value)) {
            return ListScope.SHARED;
        }
        return ListScope.assistant(uuid(value));
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ScopeErrors.invalidScope(value);
        }
    }
}
