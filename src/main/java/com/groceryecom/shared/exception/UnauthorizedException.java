package com.groceryecom.shared.exception;

/** 401: the caller's credentials or token are missing, wrong or expired. */
public class UnauthorizedException extends ApplicationException {

    public UnauthorizedException(String message) {
        super(message, "UNAUTHORIZED", 401);
    }
}
