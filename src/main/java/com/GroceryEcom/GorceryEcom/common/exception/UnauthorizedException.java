package com.GroceryEcom.GorceryEcom.common.exception;

public class UnauthorizedException extends ApplicationException {
    public UnauthorizedException(String message) {
        super(message, "UNAUTHORIZED", 401);
    }
}

