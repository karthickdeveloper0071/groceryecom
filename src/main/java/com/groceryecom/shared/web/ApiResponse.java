package com.groceryecom.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

/**
 * The single JSON envelope for every API response, success or error.
 *
 * <p>Success: {@code {"success": true, "data": ..., "timestamp": ..., "traceId": ...}}
 * <br>Error: {@code {"success": false, "code": "USERNAME_EXISTS", "message": ..., "timestamp": ..., "traceId": ...}}
 *
 * <p>The HTTP status says what happened; {@code code} is a stable, machine-readable
 * reason that clients may switch on. {@code traceId} matches the {@code X-Request-Id}
 * response header and the trace id in the server logs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        String code,
        Map<String, String> errors,
        Instant timestamp,
        String traceId) {

    /** MDC key holding the current request's trace id (set by the platform's request id filter). */
    public static final String TRACE_ID_KEY = "traceId";

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null, null, Instant.now(), MDC.get(TRACE_ID_KEY));
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, message, code, null, Instant.now(), MDC.get(TRACE_ID_KEY));
    }

    public static ApiResponse<Void> validationFailed(Map<String, String> fieldErrors) {
        return new ApiResponse<>(false, null, "Validation failed", "VALIDATION_ERROR", fieldErrors,
                Instant.now(), MDC.get(TRACE_ID_KEY));
    }
}
