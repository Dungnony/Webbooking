package com.Nhom2.booking.controller;

import com.Nhom2.booking.dto.SepayWebhookRequest;
import com.Nhom2.booking.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller nhận webhook từ Sepay khi có giao dịch ngân hàng.
 * URL này phải được cấu hình trong Sepay Dashboard:
 * https://my.sepay.vn → Webhook → URL: https://your-domain/api/webhook/sepay
 */
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentService paymentService;

    public WebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Nhận webhook từ Sepay.
     * Sepay gửi POST request mỗi khi có giao dịch mới vào tài khoản ngân hàng.
     * Endpoint này KHÔNG cần JWT (permitAll) vì Sepay gọi từ bên ngoài.
     * Bảo mật bằng API key trong header.
     */
    @PostMapping("/sepay")
    public ResponseEntity<?> handleSepayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SepayWebhookRequest request
    ) {
        try {
            // Xác thực API key từ Sepay
            if (!paymentService.verifySepayApiKey(authorization)) {
                return ResponseEntity.status(401)
                        .body(Map.of("success", false, "message", "Unauthorized"));
            }

            // Xử lý giao dịch
            boolean processed = paymentService.processWebhookPayment(request);

            if (processed) {
                return ResponseEntity.ok(Map.of("success", true, "message", "Payment confirmed"));
            } else {
                // Trả 200 OK dù không match booking (tránh Sepay retry)
                return ResponseEntity.ok(Map.of("success", true, "message", "Transaction received but no matching booking"));
            }

        } catch (Exception e) {
            log.error("[Webhook] Failed to process Sepay transaction", e);
            // Luôn trả 200 để Sepay không retry liên tục
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Error processing: " + e.getMessage()
            ));
        }
    }
}
