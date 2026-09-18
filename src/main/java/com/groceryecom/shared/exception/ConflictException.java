package com.groceryecom.shared.exception;

/** 409: the request conflicts with existing data, such as a duplicate username. */
public class ConflictException extends ApplicationException {

    public ConflictException(String message, String errorCode) {
        super(message, errorCode, 409);
    }
}
