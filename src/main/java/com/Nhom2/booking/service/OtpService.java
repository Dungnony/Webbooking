package com.Nhom2.booking.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private static final long OTP_TTL_MS = 5 * 60 * 1000;

    private record OtpEntry(String code, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final Map<String, OtpEntry> otpStorage = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String generateOtp(String email) {
        String otp = String.format("%06d", random.nextInt(1000000));
        otpStorage.put(email, new OtpEntry(otp, System.currentTimeMillis() + OTP_TTL_MS));
        return otp;
    }

    public boolean verifyOtp(String email, String otp) {
        OtpEntry entry = otpStorage.get(email);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            otpStorage.remove(email);
            return false;
        }
        return entry.code().equals(otp);
    }

    public void removeOtp(String email) {
        otpStorage.remove(email);
    }
}
