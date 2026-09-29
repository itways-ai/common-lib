package com.itways.scope;

import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the selected assistant from a request's headers: {@link ScopeHeaders#ASSISTANT}
 * first, else the legacy {@link ScopeHeaders#LEGACY_ASSISTANT}, logged at debug so
 * callers that still send the old name can be found before it is removed.
 */
@Slf4j
final class AssistantHeader {

    private AssistantHeader() {
    }

    /**
     * The header's value, or null when neither name is sent.
     *
     * @param header the request's header lookup by name (null when absent)
     */
    @SuppressWarnings("removal") // the one place the legacy name is read
    static String read(UnaryOperator<String> header) {
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
