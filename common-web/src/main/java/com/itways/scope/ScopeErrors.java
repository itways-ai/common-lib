package com.itways.scope;

import com.itways.common.exception.BusinessException;
import java.util.UUID;

/** The refusals every service gives for assistant scope, with the same codes everywhere. */
public final class ScopeErrors {

    public static final String SCOPE_REQUIRED = "SCOPE_REQUIRED";
    public static final String ASSISTANT_NOT_FOUND = "ASSISTANT_NOT_FOUND";
    public static final String INVALID_SCOPE = "INVALID_SCOPE";
    public static final String SHARED_NOT_ALLOWED = "SHARED_NOT_ALLOWED";

    private ScopeErrors() {
    }

    /** 400: a new row needs an owner, and nothing named one. */
    public static BusinessException scopeRequired() {
        return new BusinessException(
                "Choose an assistant, or mark it shared by all assistants", SCOPE_REQUIRED, 400);
    }

    /**
     * 404, not 403: whether another account has this assistant is not the
     * caller's business.
     */
    public static BusinessException assistantNotFound(UUID assistantId) {
        return new BusinessException("Assistant not found: " + assistantId, ASSISTANT_NOT_FOUND, 404);
    }

    /** 400: a {@code scope} parameter that is not {@code all}, {@code shared} or an id. */
    public static BusinessException invalidScope(String value) {
        return new BusinessException("scope must be 'all', 'shared' or an assistant id, not: " + value,
                INVALID_SCOPE, 400);
    }

    /**
     * 400 {@code SCOPE_REQUIRED}: this kind of row always belongs to one
     * assistant, and the console is on the Shared workspace, so nothing named an
     * owner. Not {@link #sharedNotAllowed}: the caller did not ask for sharing,
     * it just has to pick an owner.
     */
    public static BusinessException ownerRequired(String what) {
        return new BusinessException(
                what + " cannot live in the Shared workspace: pick an owner workspace", SCOPE_REQUIRED, 400);
    }

    /** 400: this kind of row always belongs to one assistant. */
    public static BusinessException sharedNotAllowed(String what) {
        return new BusinessException(what + " always belongs to one assistant and cannot be shared",
                SHARED_NOT_ALLOWED, 400);
    }
}
