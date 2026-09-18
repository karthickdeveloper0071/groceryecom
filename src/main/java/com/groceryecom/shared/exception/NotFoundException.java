package com.groceryecom.shared.exception;

/** 404: the requested resource does not exist (or the caller may not know it exists). */
public class NotFoundException extends ApplicationException {

    public NotFoundException(String resource, Object id) {
        super("%s not found: %s".formatted(resource, id), "NOT_FOUND", 404);
    }
}
