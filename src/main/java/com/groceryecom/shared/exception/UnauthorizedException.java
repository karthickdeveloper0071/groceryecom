package com.groceryecom.shared.exception;

public class UnauthorizedException extends ApplicationException {
    public UnauthorizedException(String message) {
        super(message, "UNAUTHORIZED", 401);
    }
}

