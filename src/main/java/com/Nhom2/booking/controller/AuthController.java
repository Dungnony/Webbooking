package com.Nhom2.booking.controller;

import com.Nhom2.booking.dto.ForgotPasswordRequest;
import com.Nhom2.booking.dto.GoogleLoginRequest;
import com.Nhom2.booking.dto.LoginRequest;
import com.Nhom2.booking.dto.RegisterRequest;
import com.Nhom2.booking.dto.ResetPasswordRequest;
import com.Nhom2.booking.dto.VerifyOtpRequest;
import com.Nhom2.booking.exception.TooManyRequestsException;
import com.Nhom2.booking.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        try {
            return ResponseEntity.ok(userService.sendOtp(request));
        } catch (TooManyRequestsException e) {
            return tooManyRequests(e);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Xác nhận OTP -> lưu user vào DB
    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@RequestBody VerifyOtpRequest request) {
        try {
            return ResponseEntity.ok(userService.verifyOtp(request.getEmail(), request.getOtp()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Login
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(userService.login(request));
        } catch (TooManyRequestsException e) {
            return tooManyRequests(e);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // QUÊN MẬT KHẨU
    // ═══════════════════════════════════════════════════════════

    // Bước 1: Nhập username -> gửi OTP đến email liên kết
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            String maskedEmail = userService.forgotPassword(request);
            return ResponseEntity.ok(maskedEmail);
        } catch (TooManyRequestsException e) {
            return tooManyRequests(e);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Bước 2: Nhập OTP + mật khẩu mới -> đặt lại mật khẩu
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            return ResponseEntity.ok(userService.resetPassword(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ĐĂNG NHẬP BẰNG GOOGLE
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/google")
    public ResponseEntity<String> googleLogin(@RequestBody GoogleLoginRequest request) {
        try {
            String token = userService.loginWithGoogle(request.getCredential());
            return ResponseEntity.ok(token);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    private static ResponseEntity<String> tooManyRequests(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
    }
}