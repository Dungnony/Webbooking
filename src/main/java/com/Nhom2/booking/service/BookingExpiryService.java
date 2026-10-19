package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.enums.BookingStatus;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pending bookings count as reserved, so an abandoned unpaid booking would block its rooms forever.
 * Cancelling them after the payment window frees the rooms again.
 */
@Service
public class BookingExpiryService {

    static final Duration PAYMENT_WINDOW = Duration.ofMinutes(30);
    private static final long CHECK_INTERVAL_MS = 60_000;

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryService.class);

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public BookingExpiryService(
            BookingRepository bookingRepository,
            NotificationService notificationService,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * Cancels PENDING + UNPAID bookings older than {@link #PAYMENT_WINDOW}. Bookings an admin already
     * CONFIRMED are left alone, since the admin accepted them knowing they were unpaid.
     */
    @Scheduled(fixedDelay = CHECK_INTERVAL_MS)
    @Transactional
    public int expireUnpaidBookings() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(PAYMENT_WINDOW);
        List<Booking> expired = bookingRepository.findByStatusAndPaymentStatusAndCreatedAtBefore(
                BookingStatus.PENDING, PaymentStatus.UNPAID, cutoff);
        if (expired.isEmpty()) {
            return 0;
        }

        for (Booking booking : expired) {
            booking.setStatus(BookingStatus.CANCELLED);
            notificationService.create(
                    booking.getUser(),
                    "notification.bookingExpired.title", "notification.bookingExpired.body",
                    booking.getId(), PAYMENT_WINDOW.toMinutes());
        }
        bookingRepository.saveAll(expired);

        log.info("Cancelled {} unpaid booking(s) older than {} minutes", expired.size(), PAYMENT_WINDOW.toMinutes());
        return expired.size();
    }
}
