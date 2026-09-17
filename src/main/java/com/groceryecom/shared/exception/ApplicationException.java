package com.groceryecom.shared.exception;

/**
 * Base custom exception for the application
 */
public class ApplicationException extends RuntimeException {
    private String errorCode;
    private int statusCode;

    public ApplicationException(String message, String errorCode, int statusCode) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    public ApplicationException(String message, Throwable cause, String errorCode, int statusCode) {
        super(message, cause);
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

