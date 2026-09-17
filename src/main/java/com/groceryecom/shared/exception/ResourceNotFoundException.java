package com.groceryecom.shared.exception;

public class ResourceNotFoundException extends ApplicationException {
    public ResourceNotFoundException(String resource, String id) {
        super(String.format("%s not found with id: %s", resource, id), "RESOURCE_NOT_FOUND", 404);
    }

    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND", 404);
    }
}

