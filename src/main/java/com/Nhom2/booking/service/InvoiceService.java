package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    static final String PDF_CONTENT_TYPE = "application/pdf";

    private final BookingRepository bookingRepository;
    private final InvoicePdfGenerator pdfGenerator;
    private final MailService mailService;

    public InvoiceService(BookingRepository bookingRepository, InvoicePdfGenerator pdfGenerator, MailService mailService) {
        this.bookingRepository = bookingRepository;
        this.pdfGenerator = pdfGenerator;
        this.mailService = mailService;
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

    // Read-only transaction so the lazy room lines are still loadable when this runs on the async listener thread.
    @Transactional(readOnly = true)
    public void emailInvoice(Long bookingId) {
        Booking booking = getPaidBooking(bookingId);
        mailService.sendMailWithAttachment(
                booking.getUser().getEmail(),
                "Xác nhận đặt phòng #" + bookingId + " - Booking Nhóm 2",
                "Xin chào " + booking.getUser().getUsername() + ",\n\n"
                        + "Chúng tôi đã nhận được thanh toán cho đơn #" + bookingId + " tại "
                        + booking.getHotel().getName() + ".\n"
                        + "Hóa đơn chi tiết được đính kèm trong email này.\n\n"
                        + "Cảm ơn bạn đã đặt phòng!",
                fileName(bookingId),
                pdfGenerator.generate(booking),
                PDF_CONTENT_TYPE);
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
