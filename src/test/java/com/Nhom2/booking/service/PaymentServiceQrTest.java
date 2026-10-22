package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.PaymentQrResponse;
import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.BookingStatus;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.repository.BookingRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceQrTest {

    private BookingRepository bookingRepository;
    private UserRepository userRepository;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        userRepository = mock(UserRepository.class);
        paymentService = new PaymentService(bookingRepository, userRepository,
                mock(NotificationService.class), mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(paymentService, "bankName", "TPBank");
        ReflectionTestUtils.setField(paymentService, "accountNumber", "10001473192");
        ReflectionTestUtils.setField(paymentService, "accountName", "NGUYEN DUY TU");
        ReflectionTestUtils.setField(paymentService, "qrTemplate", "compact2");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bankAppDeeplinkPrefillsAccountAmountAndTransferContent() {
        User alice = new User();
        alice.setId(7L);
        alice.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(bookingRepository.findById(42L)).thenReturn(Optional.of(unpaidBooking(alice)));

        PaymentQrResponse response = paymentService.generatePaymentQr(42L);

        // Sepay matches the payment by "DH <id>", so the bank app must prefill exactly that content.
        assertThat(response.getTransferContent()).isEqualTo("DH 42");
        assertThat(response.getBankAppDeeplink()).isEqualTo(
                "https://dl.vietqr.io/pay?ba=10001473192@tpb&am=1500000&tn=DH+42&bn=NGUYEN+DUY+TU");
    }

    private static Booking unpaidBooking(User owner) {
        Booking booking = new Booking();
        booking.setId(42L);
        booking.setUser(owner);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.UNPAID);
        booking.setTotalPrice(1_500_000.0);
        return booking;
    }
}
