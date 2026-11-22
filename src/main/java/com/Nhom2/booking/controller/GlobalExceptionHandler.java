package com.Nhom2.booking.controller;

import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.exception.ServiceUnavailableException;
import com.Nhom2.booking.exception.TooManyRequestsException;
import com.Nhom2.booking.exception.UnauthorizedException;
import com.Nhom2.booking.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * Errors are returned as {@code { message, code }}: {@code message} is translated for the caller,
 * {@code code} is stable so clients can react to a specific error without matching on text.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.error("Dependency failure ({})", ex.getCode(), ex);
        return localized(HttpStatus.SERVICE_UNAVAILABLE, ex);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleBusiness(BusinessException ex) {
        return localized(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", HttpStatus.CONFLICT.getReasonPhrase(), "code", "error.data.conflict"));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> handleBadInput(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", HttpStatus.BAD_REQUEST.getReasonPhrase(), "code", "error.request.invalid"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", HttpStatus.NOT_FOUND.getReasonPhrase(), "code", "error.resource.notFound"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("message", HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        log.error("Unhandled runtime error", ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(Map.of("message", status.getReasonPhrase(), "code", "error.internal"));
    }

    private ResponseEntity<Map<String, String>> localized(HttpStatus status, BusinessException ex) {
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "message", messages.get(ex.getCode(), ex.getArgs()),
                        "code", ex.getCode()));
    }
}
