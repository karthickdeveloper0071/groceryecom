package com.GroceryEcom.GorceryEcom.common.exception;

public class ValidationException extends ApplicationException {
    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR", 400);
    }

    public ValidationException(String message, String errorCode) {
        super(message, errorCode, 400);
    }
}

