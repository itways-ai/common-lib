package com.itways.common.constants;

public class ErrorCodes {
    public static final String INVALID_CREDENTIALS = "AUTH_001";
    public static final String USER_NOT_FOUND = "AUTH_002";
    public static final String USER_ALREADY_EXISTS = "AUTH_003";
    public static final String INVALID_TOKEN = "AUTH_004";
    public static final String TOKEN_EXPIRED = "AUTH_005";
    public static final String INVALID_OTP = "AUTH_006";
    public static final String OTP_EXPIRED = "AUTH_007";

    public static final String RESOURCE_NOT_FOUND = "RES_001";
    public static final String NOT_FOUND = "RES_001";
    public static final String VALIDATION_ERROR = "VAL_001";
    public static final String INTERNAL_ERROR = "SYS_001";
    public static final String UNAUTHORIZED = "AUTH_401";
    public static final String FORBIDDEN = "AUTH_403";
    public static final String ACCOUNT_LOCKED = "AUTH_008";
    public static final String CAPTCHA_INVALID = "AUTH_009";
    public static final String EXTERNAL_SERVICE_ERROR = "SYS_002";
    public static final String BAD_REQUEST = "REQ_001";

    private ErrorCodes() {
        // Utility class
    }
}
