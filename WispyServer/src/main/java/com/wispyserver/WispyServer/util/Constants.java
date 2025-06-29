package com.wispyserver.WispyServer.util;

public class Constants {

    public static final String JWT_HEADER = "Authorization";
    public static final String JWT_PREFIX = "Bearer";

    public static final String ACCOUNT_CREATED = "Account created successfully";
    public static final String LOGIN_SUCCESS = "Login successful";

    public static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERROR_BAD_REQUEST = "BAD_REQUEST";
    public static final String ERROR_NOT_FOUND = "NOT_FOUND";
    public static final String ERROR_CHARACTER_LIMIT_EXCEEDED = "CHARACTER_LIMIT_EXCEEDED";
    public static final String ERROR_CHARACTER_NOT_FOUND = "CHARACTER_NOT_FOUND";

    private Constants() {
    }
}
