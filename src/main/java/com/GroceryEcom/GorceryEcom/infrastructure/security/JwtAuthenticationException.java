package com.GroceryEcom.GorceryEcom.infrastructure.security;

import com.GroceryEcom.GorceryEcom.common.exception.ApplicationException;

/**
 * JWT Authentication Exception
 */
public class JwtAuthenticationException extends ApplicationException {

    public JwtAuthenticationException(String message) {
        super(message, "JWT_AUTH_ERROR", 401);
    }

    public JwtAuthenticationException(String message, Throwable cause) {
        super(message, cause, "JWT_AUTH_ERROR", 401);
    }
}

