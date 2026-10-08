package com.itways.scope;

import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the selected scope from a request's headers: {@link ScopeHeaders#ASSISTANT}
 * first, else the legacy {@link ScopeHeaders#LEGACY_ASSISTANT}, logged at debug so
 * callers that still send the old name can be found before it is removed.
 *
 * <p>
 * Public since 2.4.0 so a controller that takes the header as a
 * {@code @RequestHeader String} can read it the same way:
 * {@code AssistantHeader.read(request::getHeader)} or
 * {@link SelectedScope#parse(String)}.
 */
@Slf4j
public final class AssistantHeader {

    private AssistantHeader() {
    }

    /**
     * What the header selects: an assistant, the Shared workspace
     * ({@link ScopeHeaders#SHARED_VALUE}) or {@link SelectedScope#NONE} when
     * neither name is sent.
     *
     * @param header the request's header lookup by name (null when absent)
     * @throws com.itways.common.exception.BusinessException 400 {@code INVALID_SCOPE}
     *                                                       for a value that is
     *                                                       neither
     */
    public static SelectedScope read(UnaryOperator<String> header) {
        return SelectedScope.parse(raw(header));
    }

    /**
     * The header's value as sent, or null when neither name is sent. Never
     * parses, so a filter can pass any value through for the controller to
     * refuse.
     *
     * @param header the request's header lookup by name (null when absent)
     */
    @SuppressWarnings("removal") // the one place the legacy name is read
    public static String raw(UnaryOperator<String> header) {
        String value = header.apply(ScopeHeaders.ASSISTANT);
        if (value != null) {
            return value;
        }
        String legacy = header.apply(ScopeHeaders.LEGACY_ASSISTANT);
        if (legacy != null) {
            log.debug("[SCOPE] Request sent the legacy {} header; read as {}", ScopeHeaders.LEGACY_ASSISTANT,
                    ScopeHeaders.ASSISTANT);
        }
        return legacy;
    }
}
