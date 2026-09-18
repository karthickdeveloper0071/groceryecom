package com.groceryecom.platform.web;

import com.groceryecom.shared.exception.ApplicationException;
import com.groceryecom.shared.exception.TooManyRequestsException;
import com.groceryecom.shared.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns every exception into the standard {@link ApiResponse} error body.
 * Extending {@link ResponseEntityExceptionHandler} gives the correct status for all
 * standard Spring MVC errors (400, 404, 405, 406, 415, ...); this class only
 * replaces the body.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplicationException(ApplicationException ex) {
        log.debug("Request failed: {} ({})", ex.getMessage(), ex.getErrorCode());
        return ResponseEntity.status(ex.getStatusCode()).body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    /**
     * A limit was reached inside a service (the per-account login limit). The per-address
     * limits are answered by the rate limit filter, before this handler exists.
     * {@code Retry-After} tells a well-behaved client when to come back.
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    /** Thrown by method security (@PreAuthorize) after the request reached a controller. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("ACCESS_DENIED", "Access denied"));
    }

    /**
     * Two transactions wrote the same row; the second one lost. The caller should read the
     * current state and retry, so this is a conflict, not a server fault.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockFailure(ObjectOptimisticLockingFailureException ex) {
        log.info("Concurrent modification detected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("CONCURRENT_MODIFICATION",
                "The record was changed by someone else. Reload it and try again."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            errors.putIfAbsent(field, error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().headers(headers).body(ApiResponse.validationFailed(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return ResponseEntity.badRequest().headers(headers)
                .body(ApiResponse.error("MALFORMED_REQUEST", "Request body is missing or is not valid JSON"));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String code = status != null ? status.name() : "HTTP_" + statusCode.value();
        String message = status != null ? status.getReasonPhrase() : "Request failed";
        if (statusCode.is5xxServerError()) {
            log.error("Request failed with {}", statusCode, ex);
        } else {
            log.debug("Request failed with {}: {}", statusCode, ex.getMessage());
        }
        return ResponseEntity.status(statusCode).headers(headers).body(ApiResponse.error(code, message));
    }
}
