package com.Nhom2.booking.service;

import com.Nhom2.booking.event.BookingPaidEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BookingPaidListener {

    private static final Logger log = LoggerFactory.getLogger(BookingPaidListener.class);

    private final InvoiceService invoiceService;

    public BookingPaidListener(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * Runs after the payment is committed and off the request thread, so neither the Sepay webhook nor the admin
     * waits for PDF rendering and SMTP. A failure only loses the email; the guest can still download the invoice.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingPaid(BookingPaidEvent event) {
        try {
            invoiceService.emailInvoice(event.bookingId());
        } catch (RuntimeException e) {
            log.error("Could not email the invoice for booking #{}", event.bookingId(), e);
        }
    }
}
