package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.BookingStatus;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BookingExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private BookingRepository bookingRepository;
    private NotificationService notificationService;
    private BookingExpiryService expiryService;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        notificationService = mock(NotificationService.class);
        expiryService = new BookingExpiryService(
                bookingRepository, notificationService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void looksForPendingUnpaidBookingsOlderThanThePaymentWindow() {
        when(bookingRepository.findByStatusAndPaymentStatusAndCreatedAtBefore(any(), any(), any()))
                .thenReturn(List.of());

        expiryService.expireUnpaidBookings();

        LocalDateTime expectedCutoff = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
                .minus(BookingExpiryService.PAYMENT_WINDOW);
        verify(bookingRepository).findByStatusAndPaymentStatusAndCreatedAtBefore(
                BookingStatus.PENDING, PaymentStatus.UNPAID, expectedCutoff);
    }

    @Test
    void cancelsExpiredBookingsAndTellsTheirOwners() {
        User alice = new User();
        alice.setUsername("alice");
        Booking stale = booking(7L, alice);
        when(bookingRepository.findByStatusAndPaymentStatusAndCreatedAtBefore(any(), any(), any()))
                .thenReturn(List.of(stale));

        int expired = expiryService.expireUnpaidBookings();

        assertThat(expired).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).saveAll(List.of(stale));
        verify(notificationService).create(
                eq(alice), eq("notification.bookingExpired.title"), eq("notification.bookingExpired.body"),
                eq(7L), eq(BookingExpiryService.PAYMENT_WINDOW.toMinutes()));
    }

    @Test
    void doesNothingWhenNoBookingHasExpired() {
        when(bookingRepository.findByStatusAndPaymentStatusAndCreatedAtBefore(any(), any(), any()))
                .thenReturn(List.of());

        assertThat(expiryService.expireUnpaidBookings()).isZero();
        verifyNoInteractions(notificationService);
    }

    private static Booking booking(Long id, User user) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setUser(user);
        booking.setStatus(BookingStatus.PENDING);
        booking.setPaymentStatus(PaymentStatus.UNPAID);
        return booking;
    }
}
