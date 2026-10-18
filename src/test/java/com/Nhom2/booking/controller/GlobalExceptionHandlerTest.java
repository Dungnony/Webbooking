package com.Nhom2.booking.controller;

import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundMapsTo404WithMessage() {
        ResponseEntity<Map<String, String>> response = handler.handleNotFound(new NotFoundException("Booking not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", "Booking not found");
    }

    @Test
    void forbiddenMapsTo403WithMessage() {
        ResponseEntity<Map<String, String>> response = handler.handleForbidden(new ForbiddenException("Not yours"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("message", "Not yours");
    }

    @Test
    void businessRuleViolationMapsTo400() {
        ResponseEntity<Map<String, String>> response = handler.handleRuntime(new RuntimeException("Invalid dates"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Invalid dates");
    }

    @Test
    void nullMessageFallsBackToReasonPhraseInsteadOfFailing() {
        ResponseEntity<Map<String, String>> response = handler.handleRuntime(new RuntimeException());

        assertThat(response.getBody()).containsEntry("message", HttpStatus.BAD_REQUEST.getReasonPhrase());
    }
}
