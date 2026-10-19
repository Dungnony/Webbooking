package com.Nhom2.booking.controller;

import com.Nhom2.booking.dto.ForgotPasswordRequest;
import com.Nhom2.booking.dto.GoogleLoginRequest;
import com.Nhom2.booking.dto.LoginRequest;
import com.Nhom2.booking.dto.RegisterRequest;
import com.Nhom2.booking.dto.ResetPasswordRequest;
import com.Nhom2.booking.dto.VerifyOtpRequest;
import com.Nhom2.booking.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Failures are rendered by GlobalExceptionHandler as a translated {@code { message, code }} body. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // Đăng ký -> gửi OTP
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.sendOtp(request));
    }

    // Xác nhận OTP -> lưu user vào DB
    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(userService.verifyOtp(request.getEmail(), request.getOtp()));
    }

    // Login
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    // ═══════════════════════════════════════════════════════════
    // QUÊN MẬT KHẨU
    // ═══════════════════════════════════════════════════════════

    // Bước 1: Nhập username -> gửi OTP đến email liên kết
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(userService.forgotPassword(request));
    }

    // Bước 2: Nhập OTP + mật khẩu mới -> đặt lại mật khẩu
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(userService.resetPassword(request));
    }

    // ═══════════════════════════════════════════════════════════
    // ĐĂNG NHẬP BẰNG GOOGLE
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/google")
    public ResponseEntity<String> googleLogin(@RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(userService.loginWithGoogle(request.getCredential()));
    }
}
