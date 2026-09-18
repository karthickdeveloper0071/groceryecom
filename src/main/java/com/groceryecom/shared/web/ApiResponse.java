package com.groceryecom.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

/**
 * The single JSON envelope for every API response, success or error.
 * The HTTP status code is the source of truth for the outcome; {@code errorCode}
 * is a stable, machine-readable reason that clients can switch on.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        String errorCode,
        Map<String, String> errors,
        Instant timestamp,
        String requestId) {

    /** MDC key holding the current request's correlation id (set by the platform's request id filter). */
    public static final String REQUEST_ID_KEY = "requestId";

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null, null, Instant.now(), MDC.get(REQUEST_ID_KEY));
    }

    public static ApiResponse<Void> error(String errorCode, String message) {
        return new ApiResponse<>(false, null, message, errorCode, null, Instant.now(), MDC.get(REQUEST_ID_KEY));
    }

    public static ApiResponse<Void> validationFailed(Map<String, String> fieldErrors) {
        return new ApiResponse<>(false, null, "Validation failed", "VALIDATION_ERROR", fieldErrors,
                Instant.now(), MDC.get(REQUEST_ID_KEY));
    }
}
