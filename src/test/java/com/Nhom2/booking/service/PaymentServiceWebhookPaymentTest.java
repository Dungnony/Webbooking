package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.SepayWebhookRequest;
import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.BookingStatus;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.event.BookingPaidEvent;
import com.Nhom2.booking.repository.BookingRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceWebhookPaymentTest {

    private BookingRepository bookingRepository;
    private NotificationService notificationService;
    private ApplicationEventPublisher eventPublisher;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        notificationService = mock(NotificationService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        paymentService = new PaymentService(bookingRepository, mock(UserRepository.class), notificationService, eventPublisher);
    }

    @Test
    void marksPendingBookingPaidWhenTheFullAmountArrives() {
        Booking booking = booking(BookingStatus.PENDING);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        assertThat(paymentService.processWebhookPayment(transfer("DH 5", 1_000_000L))).isTrue();
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        // Triggers the invoice email once the transaction commits.
        verify(eventPublisher).publishEvent(new BookingPaidEvent(5L));
    }

    @Test
    void insufficientTransferDoesNotTriggerAnInvoice() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING)));

        assertThat(paymentService.processWebhookPayment(transfer("DH 5", 10_000L))).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void moneyForAnExpiredBookingIsFlaggedToAdminsForRefund() {
        Booking booking = booking(BookingStatus.CANCELLED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        assertThat(paymentService.processWebhookPayment(transfer("DH 5", 1_000_000L))).isFalse();

        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        verify(notificationService).notifyAdmins(anyString(), contains("#5"));
        verify(bookingRepository, never()).save(any());
    }

    private static Booking booking(BookingStatus status) {
        User owner = new User();
        owner.setUsername("alice");
        Booking booking = new Booking();
        booking.setId(5L);
        booking.setUser(owner);
        booking.setStatus(status);
        booking.setPaymentStatus(PaymentStatus.UNPAID);
        booking.setTotalPrice(1_000_000.0);
        return booking;
    }

    private static SepayWebhookRequest transfer(String content, long amount) {
        SepayWebhookRequest request = new SepayWebhookRequest();
        request.setTransferType("in");
        request.setContent(content);
        request.setTransferAmount(amount);
        return request;
    }
}
