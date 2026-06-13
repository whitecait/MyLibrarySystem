package com.library.exception;

/**
 * Business-layer exception — caught by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final int code;

    public ApiException(String message) {
        super(message);
        this.code = 1;
    }

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
