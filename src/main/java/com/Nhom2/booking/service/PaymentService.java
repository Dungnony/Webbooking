package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.PaymentQrResponse;
import com.Nhom2.booking.dto.SepayWebhookRequest;
import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.enums.BookingStatus;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.event.BookingPaidEvent;
import com.Nhom2.booking.exception.ForbiddenException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.BookingRepository;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final List<String> SEPAY_AUTH_SCHEMES = List.of("Apikey ", "Bearer ");

    // TPBank BIN code theo chuẩn VietQR
    private static final String BANK_BIN = "970423";

    // Pattern để trích xuất booking ID từ nội dung chuyển khoản
    // Tìm "DH" theo sau bởi số, ví dụ: "DH 123", "DH123", "DH  123"
    private static final Pattern BOOKING_ID_PATTERN =
            Pattern.compile("DH\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    @Value("${payment.bank.name:TPBank}")
    private String bankName;

    @Value("${payment.bank.account-number:10001473192}")
    private String accountNumber;

    @Value("${payment.bank.account-name:NGUYEN DUY TU}")
    private String accountName;

    @Value("${payment.qr.template:compact2}")
    private String qrTemplate;

    @Value("${payment.sepay.api-key:}")
    private String sepayApiKey;

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Tạo mã QR thanh toán cho booking
     * Sử dụng VietQR API (miễn phí, không cần đăng ký)
     */
    public PaymentQrResponse generatePaymentQr(Long bookingId) {

        User user = getCurrentUser();
        Booking booking = getBooking(bookingId);

        // Chỉ chủ booking mới được xem QR
        if (!booking.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You can only access your own booking");
        }

        // Chỉ booking CONFIRMED hoặc PENDING + UNPAID mới được thanh toán
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
            throw new RuntimeException(
                    "Booking must be pending or confirmed before payment. Current status: "
                            + booking.getStatus()
            );
        }

        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            throw new RuntimeException("Booking has already been paid");
        }

        // Nội dung chuyển khoản: DH {bookingId}
        String transferContent = "DH " + bookingId;
        long amount = booking.getTotalPrice().longValue();

        // Tạo URL mã QR theo VietQR API
        String qrCodeUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-%s.png?amount=%d&addInfo=%s&accountName=%s",
                BANK_BIN,
                accountNumber,
                qrTemplate,
                amount,
                URLEncoder.encode(transferContent, StandardCharsets.UTF_8),
                URLEncoder.encode(accountName, StandardCharsets.UTF_8)
        );

        PaymentQrResponse response = new PaymentQrResponse();
        response.setBookingId(bookingId);
        response.setQrCodeUrl(qrCodeUrl);
        response.setBankName(bankName);
        response.setAccountNumber(accountNumber);
        response.setAccountName(accountName);
        response.setAmount(booking.getTotalPrice());
        response.setTransferContent(transferContent);

        return response;
    }

    /**
     * User xác nhận đã thanh toán → thông báo cho admin
     */
    public Booking confirmPaymentByUser(Long bookingId) {

        User user = getCurrentUser();
        Booking booking = getBooking(bookingId);

        if (!booking.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You can only access your own booking");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
            throw new RuntimeException("Booking must be pending or confirmed before payment");
        }

        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            throw new RuntimeException("Booking has already been paid");
        }

        // Thông báo cho admin kiểm tra thanh toán
        notificationService.notifyAdmins(
                "Payment notification",
                "User " + user.getUsername()
                        + " has transferred payment for booking #" + bookingId
                        + ". Amount: " + String.format("%,.0f", booking.getTotalPrice())
                        + " VND. Please verify and confirm."
        );

        return booking;
    }

    // ==================== SEPAY WEBHOOK ====================

    /**
     * Xác thực API key từ Sepay webhook.
     * API key được cấu hình trong Sepay Dashboard và application.properties.
     */
    public boolean verifySepayApiKey(String authorization) {
        // Fail closed: an unconfigured key must never let anonymous callers mark bookings as paid.
        if (sepayApiKey == null || sepayApiKey.isBlank()) {
            log.error("[Webhook] Rejected: payment.sepay.api-key is not configured");
            return false;
        }

        if (authorization == null || authorization.isBlank()) {
            log.warn("[Webhook] Rejected: missing Authorization header");
            return false;
        }

        String token = stripAuthScheme(authorization.trim());
        boolean valid = MessageDigest.isEqual(
                sepayApiKey.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));

        if (!valid) {
            log.warn("[Webhook] Rejected: invalid API key");
        }

        return valid;
    }

    // Sepay sends "Apikey <key>"; "Bearer <key>" is accepted for manual testing.
    private String stripAuthScheme(String authorization) {
        for (String scheme : SEPAY_AUTH_SCHEMES) {
            if (authorization.regionMatches(true, 0, scheme, 0, scheme.length())) {
                return authorization.substring(scheme.length()).trim();
            }
        }
        return authorization;
    }

    /**
     * Xử lý webhook từ Sepay:
     * 1. Parse nội dung chuyển khoản → tìm booking ID
     * 2. Kiểm tra số tiền khớp
     * 3. Tự động cập nhật paymentStatus = PAID
     * 4. Gửi thông báo cho user
     *
     * @return true nếu tìm thấy và xử lý booking thành công
     */
    @Transactional
    public boolean processWebhookPayment(SepayWebhookRequest request) {
        // Chỉ xử lý giao dịch tiền VÀO (transferType = "in")
        if (!"in".equalsIgnoreCase(request.getTransferType())) {
            log.info("[Webhook] Skipped: not an incoming transfer");
            return false;
        }

        String content = request.getContent();
        if (content == null || content.isBlank()) {
            log.info("[Webhook] Skipped: empty transfer content");
            return false;
        }

        log.info("[Webhook] Received transaction: amount={}, content=\"{}\"",
                request.getTransferAmount(), content);

        // Trích xuất booking ID từ nội dung: "DH 123" → 123
        Long bookingId = extractBookingId(content);
        if (bookingId == null) {
            log.info("[Webhook] Skipped: no booking ID found in content");
            return false;
        }

        // Tìm booking
        Optional<Booking> optionalBooking = bookingRepository.findById(bookingId);
        if (optionalBooking.isEmpty()) {
            log.info("[Webhook] Skipped: booking #{} not found", bookingId);
            return false;
        }

        Booking booking = optionalBooking.get();

        long receivedAmount = request.getTransferAmount() != null
                ? request.getTransferAmount() : 0;

        // Kiểm tra booking đã CONFIRMED hoặc PENDING và chưa PAID
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
            log.warn("[Webhook] Booking #{} is {} but received {} VND",
                    bookingId, booking.getStatus(), receivedAmount);
            // Money arrived after the booking expired or was cancelled: someone has to refund it.
            notificationService.notifyAdmins(
                    "Payment for inactive booking",
                    "Booking #" + bookingId + " is " + booking.getStatus()
                            + " but received " + String.format("%,d", receivedAmount) + " VND."
                            + " Please refund or restore the booking manually.");
            return false;
        }

        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("[Webhook] Skipped: booking #{} already paid", bookingId);
            return false;
        }

        // Kiểm tra số tiền (cho phép >= totalPrice để tránh sai lệch nhỏ)
        long expectedAmount = booking.getTotalPrice().longValue();

        if (receivedAmount < expectedAmount) {
            log.warn("[Webhook] Booking #{} received {} but expected {}",
                    bookingId, receivedAmount, expectedAmount);

            // Vẫn thông báo admin để xử lý thủ công
            notificationService.notifyAdmins(
                    "⚠️ Insufficient payment",
                    "Booking #" + bookingId
                            + " received " + String.format("%,d", receivedAmount) + " VND"
                            + " but expected " + String.format("%,d", expectedAmount) + " VND."
                            + " Please verify manually."
            );
            return false;
        }

        // ✅ TỰ ĐỘNG XÁC NHẬN THANH TOÁN
        booking.setPaymentStatus(PaymentStatus.PAID);
        bookingRepository.save(booking);
        eventPublisher.publishEvent(new BookingPaidEvent(bookingId));

        log.info("[Webhook] Booking #{} auto-confirmed PAID (received: {} VND)",
                bookingId, receivedAmount);

        // Thông báo cho user
        notificationService.create(
                booking.getUser(),
                "✅ Payment confirmed",
                "Your payment of " + String.format("%,d", receivedAmount)
                        + " VND for booking #" + bookingId
                        + " has been automatically confirmed. Thank you!"
        );

        // Thông báo cho admin
        notificationService.notifyAdmins(
                "✅ Auto payment confirmed",
                "Booking #" + bookingId
                        + " payment auto-confirmed via bank transfer."
                        + " Amount: " + String.format("%,d", receivedAmount) + " VND."
        );

        return true;
    }

    /**
     * Trích xuất booking ID từ nội dung chuyển khoản.
     * Hỗ trợ: "DH 123", "DH123", "thanh toan DH 123 abc", ...
     */
    private Long extractBookingId(String content) {
        Matcher matcher = BOOKING_ID_PATTERN.matcher(content);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Booking getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
