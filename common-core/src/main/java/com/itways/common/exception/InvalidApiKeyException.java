package com.itways.common.exception;

public class InvalidApiKeyException extends BusinessException {
    public InvalidApiKeyException() {
        super("Invalid API Key", "INVALID_API_KEY", 401);
    }

    public InvalidApiKeyException(String message) {
        super(message, "INVALID_API_KEY", 401);
    }
}
