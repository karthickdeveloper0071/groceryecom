package com.groceryecom.shared.exception;

/** 400: the request is well-formed but breaks a business rule. */
public class ValidationException extends ApplicationException {

    public ValidationException(String message) {
        this(message, "VALIDATION_ERROR");
    }

    public ValidationException(String message, String errorCode) {
        super(message, errorCode, 400);
    }
}
