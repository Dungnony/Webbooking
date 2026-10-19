package com.Nhom2.booking.service;

import com.Nhom2.booking.event.BookingPaidEvent;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BookingPaidListenerTest {

    private final InvoiceService invoiceService = mock(InvoiceService.class);
    private final BookingPaidListener listener = new BookingPaidListener(invoiceService);

    @Test
    void emailsTheInvoiceWhenABookingIsPaid() {
        listener.onBookingPaid(new BookingPaidEvent(42L));

        verify(invoiceService).emailInvoice(42L);
    }

    @Test
    void mailFailureIsLoggedAndDoesNotPropagate() {
        // The payment is already committed; a mail outage must not surface as an error to the payer or webhook.
        doThrow(new MailSendException("SMTP down")).when(invoiceService).emailInvoice(42L);

        assertThatCode(() -> listener.onBookingPaid(new BookingPaidEvent(42L))).doesNotThrowAnyException();
    }
}
