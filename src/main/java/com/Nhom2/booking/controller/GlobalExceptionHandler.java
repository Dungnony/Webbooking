package com.Nhom2.booking.controller;

import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.exception.TooManyRequestsException;
import com.Nhom2.booking.exception.UnauthorizedException;
import com.Nhom2.booking.i18n.Messages;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Errors are returned as {@code { message, code }}: {@code message} is translated for the caller,
 * {@code code} is stable so clients can react to a specific error without matching on text.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Messages messages;

    public GlobalExceptionHandler(Messages messages) {
        this.messages = messages;
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
        return localized(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException ex) {
        return localized(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(UnauthorizedException ex) {
        return localized(HttpStatus.UNAUTHORIZED, ex);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, String>> handleTooManyRequests(TooManyRequestsException ex) {
        return localized(HttpStatus.TOO_MANY_REQUESTS, ex);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleBusiness(BusinessException ex) {
        return localized(HttpStatus.BAD_REQUEST, ex);
    }

    // Other RuntimeExceptions (framework and internal errors) keep their raw message and also map to 400.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = ex.getMessage() == null ? status.getReasonPhrase() : ex.getMessage();
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    private ResponseEntity<Map<String, String>> localized(HttpStatus status, BusinessException ex) {
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "message", messages.get(ex.getCode(), ex.getArgs()),
                        "code", ex.getCode()));
    }
}
