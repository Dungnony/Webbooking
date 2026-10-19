package com.Nhom2.booking.service;

import com.Nhom2.booking.config.LocaleConfig;
import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.i18n.Messages;
import com.Nhom2.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceServiceTest {

    private static final byte[] PDF = "%PDF-fake".getBytes();

    private BookingRepository bookingRepository;
    private InvoicePdfGenerator pdfGenerator;
    private MailService mailService;
    private InvoiceService invoiceService;
    private Booking booking;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        pdfGenerator = mock(InvoicePdfGenerator.class);
        mailService = mock(MailService.class);
        invoiceService = new InvoiceService(
                bookingRepository, pdfGenerator, mailService, new Messages(LocaleConfig.createMessageSource()));

        booking = InvoicePdfGeneratorTest.sampleBooking();
        when(bookingRepository.findById(42L)).thenReturn(Optional.of(booking));
        when(pdfGenerator.generate(booking)).thenReturn(PDF);
    }

    @Test
    void ownerCanDownloadTheInvoiceOfAPaidBooking() {
        assertThat(invoiceService.generateForOwner("alice", 42L)).isEqualTo(PDF);
    }

    @Test
    void anotherUserCannotDownloadTheInvoice() {
        assertThatThrownBy(() -> invoiceService.generateForOwner("mallory", 42L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unpaidBookingHasNoInvoice() {
        booking.setPaymentStatus(PaymentStatus.UNPAID);

        assertThatThrownBy(() -> invoiceService.generateForOwner("alice", 42L))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> invoiceService.emailInvoice(42L)).isInstanceOf(RuntimeException.class);
        verify(mailService, never()).sendMailWithAttachment(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownBookingIsNotFound() {
        when(bookingRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.generateForOwner("alice", 7L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void emailsThePdfToTheBookingOwner() {
        invoiceService.emailInvoice(42L);

        verify(mailService).sendMailWithAttachment(
                eq("alice@example.com"),
                contains("#42"),
                anyString(),
                eq("hoa-don-42.pdf"),
                eq(PDF),
                eq("application/pdf"));
    }

    @Test
    void invoiceMailIsWrittenInVietnameseAndEnglishBecauseTheGuestLanguageIsUnknown() {
        invoiceService.emailInvoice(42L);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendMailWithAttachment(
                anyString(), subject.capture(), body.capture(), anyString(), any(), anyString());
        assertThat(subject.getValue()).contains("Xác nhận đặt phòng #42").contains("Booking confirmation #42");
        assertThat(body.getValue()).contains("Xin chào alice").contains("Hello alice");
    }
}
