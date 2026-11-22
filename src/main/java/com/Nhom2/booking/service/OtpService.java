package com.Nhom2.booking.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    // Matches the "valid for 5 minutes" promise in the OTP mails.
    static final Duration OTP_TTL = Duration.ofMinutes(5);
    // A 6-digit code has 1,000,000 values; capping guesses per code makes brute force impractical.
    static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, OtpEntry> otpStorage = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public OtpService() {
        this(Clock.systemUTC());
    }

    OtpService(Clock clock) {
        this.clock = clock;
    }

    public String generateOtp(String key) {
        String normalized = normalizeKey(key);
        if (otpStorage.size() >= MAX_ENTRIES) {
            Instant now = clock.instant();
            otpStorage.entrySet().removeIf(e -> !now.isBefore(e.getValue().expiresAt()));
        }
        String otp = String.format("%06d", random.nextInt(1000000));
        otpStorage.put(normalized, new OtpEntry(otp, clock.instant().plus(OTP_TTL), 0));
        return otp;
    }

    /**
     * Expired codes and codes with too many wrong guesses are discarded, so the caller must request a new one.
     * A correct code stays stored until {@link #removeOtp} so the caller can finish its own checks first.
     */
    public boolean verifyOtp(String key, String otp) {
        return verifyInternal(key, otp, false);
    }

    public boolean verifyAndConsumeOtp(String key, String otp) {
        return verifyInternal(key, otp, true);
    }

    private boolean verifyInternal(String key, String otp, boolean consume) {
        if (otp == null || key == null) {
            return false;
        }
        boolean[] matched = {false};
        otpStorage.computeIfPresent(normalizeKey(key), (k, entry) -> {
            if (!clock.instant().isBefore(entry.expiresAt())) {
                return null;
            }
            if (constantTimeEquals(entry.code(), otp)) {
                matched[0] = true;
                return consume ? null : entry;
            }
            int failedAttempts = entry.failedAttempts() + 1;
            return failedAttempts >= MAX_FAILED_ATTEMPTS ? null : entry.withFailedAttempts(failedAttempts);
        });
        return matched[0];
    }

    public void removeOtp(String key) {
        if (key != null) {
            otpStorage.remove(normalizeKey(key));
        }
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private record OtpEntry(String code, Instant expiresAt, int failedAttempts) {
        OtpEntry withFailedAttempts(int count) {
            return new OtpEntry(code, expiresAt, count);
        }
    }
}
