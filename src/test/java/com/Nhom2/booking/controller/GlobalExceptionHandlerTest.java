package com.Nhom2.booking.controller;

import com.Nhom2.booking.config.LocaleConfig;
import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.exception.TooManyRequestsException;
import com.Nhom2.booking.exception.UnauthorizedException;
import com.Nhom2.booking.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new Messages(LocaleConfig.createMessageSource()));

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void notFoundMapsTo404WithTranslatedMessageAndCode() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        ResponseEntity<Map<String, String>> response =
                handler.handleNotFound(new NotFoundException("error.booking.notFound"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .containsEntry("message", "Booking not found.")
                .containsEntry("code", "error.booking.notFound");
    }

    @Test
    void messageFollowsTheRequestLanguage() {
        BusinessException ex = new BusinessException("error.booking.checkInPast");

        LocaleContextHolder.setLocale(LocaleConfig.DEFAULT_LOCALE);
        assertThat(handler.handleBusiness(ex).getBody())
                .containsEntry("message", "Ngày nhận phòng không được ở trong quá khứ.");

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(handler.handleBusiness(ex).getBody())
                .containsEntry("message", "The check-in date cannot be in the past.");
    }

    @Test
    void argumentsAreFilledIntoTheMessage() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        ResponseEntity<Map<String, String>> response =
                handler.handleBusiness(new BusinessException("error.review.ratingRange", 1, 10));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "The rating must be between 1 and 10.");
    }

    @Test
    void unsupportedLanguageFallsBackToVietnameseNotTheServerLocale() {
        LocaleContextHolder.setLocale(Locale.FRENCH);

        assertThat(handler.handleBusiness(new BusinessException("error.otp.invalid")).getBody())
                .containsEntry("message", "Mã OTP không đúng hoặc đã hết hạn.");
    }

    @Test
    void unknownCodeIsReturnedAsIsInsteadOfFailing() {
        assertThat(handler.handleBusiness(new BusinessException("error.does.not.exist")).getBody())
                .containsEntry("message", "error.does.not.exist");
    }

    @Test
    void forbiddenUnauthorizedAndRateLimitKeepTheirStatus() {
        assertThat(handler.handleForbidden(new ForbiddenException("error.booking.notOwner")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleUnauthorized(new UnauthorizedException("error.login.invalidCredentials")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleTooManyRequests(new TooManyRequestsException("error.login.locked")).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void otherRuntimeExceptionsKeepTheirRawMessageAndMapTo400() {
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
