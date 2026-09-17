package com.GroceryEcom.GorceryEcom.common.exception;

import com.GroceryEcom.GorceryEcom.common.util.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global Exception Handler for all REST controllers
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<?>> handleApplicationException(
            ApplicationException ex, WebRequest request) {

        log.error("Application Exception: {}", ex.getMessage(), ex);

        ApiResponse<?> response = ApiResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode(ex.getErrorCode())
                .statusCode(ex.getStatusCode())
                .requestId(generateRequestId())
                .build();

        return new ResponseEntity<>(response,
            HttpStatus.resolve(ex.getStatusCode()) != null ?
            HttpStatus.resolve(ex.getStatusCode()) : HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ApiResponse<?> response = ApiResponse.builder()
                .success(false)
                .message("Validation failed")
                .errorCode("VALIDATION_ERROR")
                .errors(errors)
                .statusCode(400)
                .requestId(generateRequestId())
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {

        log.warn("Access denied: {}", ex.getMessage());

        ApiResponse<?> response = ApiResponse.builder()
                .success(false)
                .message("Access Denied")
                .errorCode("ACCESS_DENIED")
                .statusCode(403)
                .requestId(generateRequestId())
                .build();

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoHandlerFoundException(
            NoHandlerFoundException ex, WebRequest request) {

        ApiResponse<?> response = ApiResponse.builder()
                .success(false)
                .message("Endpoint not found")
                .errorCode("NOT_FOUND")
                .statusCode(404)
                .requestId(generateRequestId())
                .build();

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGlobalException(
            Exception ex, WebRequest request) {

        log.error("Unexpected error occurred", ex);

        ApiResponse<?> response = ApiResponse.builder()
                .success(false)
                .message("An unexpected error occurred")
                .errorCode("INTERNAL_SERVER_ERROR")
                .statusCode(500)
                .requestId(generateRequestId())
                .build();

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }
}

