package com.groceryecom.shared.exception;

/**
 * Base for expected business errors. The platform's exception handler turns these
 * into an error response with the given HTTP status and a stable error code.
 * Unexpected failures should not use this type; they surface as 500 responses.
 */
public abstract class ApplicationException extends RuntimeException {

    private final String errorCode;
    private final int statusCode;

    protected ApplicationException(String message, String errorCode, int statusCode) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
