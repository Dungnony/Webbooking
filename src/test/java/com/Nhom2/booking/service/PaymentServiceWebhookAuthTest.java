package com.Nhom2.booking.service;

import com.Nhom2.booking.repository.BookingRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PaymentServiceWebhookAuthTest {

    private static final String API_KEY = "test-sepay-key";

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                mock(BookingRepository.class),
                mock(UserRepository.class),
                mock(NotificationService.class),
                mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(paymentService, "sepayApiKey", API_KEY);
    }

    @Test
    void rejectsEveryRequestWhenApiKeyIsNotConfigured() {
        ReflectionTestUtils.setField(paymentService, "sepayApiKey", "");

        assertThat(paymentService.verifySepayApiKey(null)).isFalse();
        assertThat(paymentService.verifySepayApiKey("Apikey anything")).isFalse();
    }

    @Test
    void acceptsSepayApikeyScheme() {
        assertThat(paymentService.verifySepayApiKey("Apikey " + API_KEY)).isTrue();
    }

    @Test
    void acceptsBearerSchemeCaseInsensitively() {
        assertThat(paymentService.verifySepayApiKey("bearer " + API_KEY)).isTrue();
    }

    @Test
    void rejectsMissingHeader() {
        assertThat(paymentService.verifySepayApiKey(null)).isFalse();
        assertThat(paymentService.verifySepayApiKey("  ")).isFalse();
    }

    @Test
    void rejectsWrongKey() {
        assertThat(paymentService.verifySepayApiKey("Apikey wrong-key")).isFalse();
    }

    @Test
    void doesNotStripSchemeAppearingInsideTheKey() {
        assertThat(paymentService.verifySepayApiKey("Apikey test-Apikey sepay-key")).isFalse();
    }
}
