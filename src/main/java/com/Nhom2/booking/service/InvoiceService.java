package com.Nhom2.booking.service;

import com.Nhom2.booking.config.LocaleConfig;
import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.i18n.Messages;
import com.Nhom2.booking.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class InvoiceService {

    static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String BILINGUAL_BODY_SEPARATOR = "\n\n--------------------\n\n";

    private final BookingRepository bookingRepository;
    private final InvoicePdfGenerator pdfGenerator;
    private final MailService mailService;
    private final Messages messages;

    public InvoiceService(
            BookingRepository bookingRepository,
            InvoicePdfGenerator pdfGenerator,
            MailService mailService,
            Messages messages) {
        this.bookingRepository = bookingRepository;
        this.pdfGenerator = pdfGenerator;
        this.mailService = mailService;
        this.messages = messages;
    }

    /** Lets the guest download the invoice of their own paid booking again. */
    @Transactional(readOnly = true)
    public byte[] generateForOwner(String username, Long bookingId) {
        Booking booking = getPaidBooking(bookingId);
        if (booking.getUser() == null || !booking.getUser().getUsername().equals(username)) {
            throw new ForbiddenException("error.booking.notOwner");
        }
        return pdfGenerator.generate(booking);
    }

    /**
     * Sent from a background thread after a webhook or an admin action, so the guest's UI language is unknown;
     * like the PDF, the mail is written in both Vietnamese and English.
     * Read-only transaction so the lazy room lines are still loadable on the async listener thread.
     */
    @Transactional(readOnly = true)
    public void emailInvoice(Long bookingId) {
        Booking booking = getPaidBooking(bookingId);
        Object[] args = {booking.getUser().getUsername(), String.valueOf(bookingId), booking.getHotel().getName()};
        mailService.sendMailWithAttachment(
                booking.getUser().getEmail(),
                bilingual("mail.invoice.subject", " / ", args),
                bilingual("mail.invoice.body", BILINGUAL_BODY_SEPARATOR, args),
                fileName(bookingId),
                pdfGenerator.generate(booking),
                PDF_CONTENT_TYPE);
    }

    private String bilingual(String code, String separator, Object... args) {
        return messages.forLocale(LocaleConfig.DEFAULT_LOCALE, code, args)
                + separator
                + messages.forLocale(Locale.ENGLISH, code, args);
    }

    public static String fileName(Long bookingId) {
        return "hoa-don-" + bookingId + ".pdf";
    }

    private Booking getPaidBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("error.booking.notFound"));
        if (booking.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BusinessException("error.invoice.notPaid");
        }
        return booking;
    }
}
