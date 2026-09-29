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
 * {@link ScopeHeaders#LEGACY_ASSISTANT} (2.1.0 transition).
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
        return resolve(webRequest.getParameter(ScopeHeaders.SCOPE_PARAM), AssistantHeader.read(webRequest::getHeader));
    }

    /** The list a request asked for; malformed values are a 400, never "everything". */
    public static ListScope resolve(String scopeParam, String assistantHeader) {
        if (scopeParam != null && !scopeParam.isBlank()) {
            String value = scopeParam.trim();
            if ("all".equalsIgnoreCase(value)) {
                return ListScope.ALL;
            }
            if ("shared".equalsIgnoreCase(value)) {
                return ListScope.SHARED;
            }
            return ListScope.assistant(uuid(value));
        }
        if (assistantHeader != null && !assistantHeader.isBlank()) {
            return ListScope.assistant(uuid(assistantHeader.trim()));
        }
        return ListScope.ALL;
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ScopeErrors.invalidScope(value);
        }
    }
}
