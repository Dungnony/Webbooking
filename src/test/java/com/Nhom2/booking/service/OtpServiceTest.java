package com.Nhom2.booking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class OtpServiceTest {

    private static final String KEY = "reset:alice";

    private MutableClock clock;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        otpService = new OtpService(clock);
    }

    @Test
    void correctOtpIsAccepted() {
        String otp = otpService.generateOtp(KEY);

        assertThat(otp).matches("\\d{6}");
        assertThat(otpService.verifyOtp(KEY, otp)).isTrue();
    }

    @Test
    void otpExpiresAfterTheTimeToLive() {
        String otp = otpService.generateOtp(KEY);

        clock.advance(OtpService.OTP_TTL.plusSeconds(1));

        assertThat(otpService.verifyOtp(KEY, otp)).isFalse();
    }

    @Test
    void otpIsStillValidJustBeforeExpiry() {
        String otp = otpService.generateOtp(KEY);

        clock.advance(OtpService.OTP_TTL.minusSeconds(1));

        assertThat(otpService.verifyOtp(KEY, otp)).isTrue();
    }

    @Test
    void otpIsInvalidatedAfterTooManyWrongGuesses() {
        String otp = otpService.generateOtp(KEY);
        String wrong = otp.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < OtpService.MAX_FAILED_ATTEMPTS; i++) {
            assertThat(otpService.verifyOtp(KEY, wrong)).isFalse();
        }

        // Even the right code no longer works: brute force cannot continue against the same OTP.
        assertThat(otpService.verifyOtp(KEY, otp)).isFalse();
    }

    @Test
    void fewerWrongGuessesThanTheLimitDoNotLockTheOtp() {
        String otp = otpService.generateOtp(KEY);
        String wrong = otp.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < OtpService.MAX_FAILED_ATTEMPTS - 1; i++) {
            otpService.verifyOtp(KEY, wrong);
        }

        assertThat(otpService.verifyOtp(KEY, otp)).isTrue();
    }

    @Test
    void requestingANewOtpReplacesTheOldOne() {
        String first = otpService.generateOtp(KEY);
        String second = otpService.generateOtp(KEY);

        assertThat(otpService.verifyOtp(KEY, second)).isTrue();
        if (!first.equals(second)) {
            assertThat(otpService.verifyOtp(KEY, first)).isFalse();
        }
    }

    @Test
    void removedOrUnknownOtpIsRejected() {
        String otp = otpService.generateOtp(KEY);
        otpService.removeOtp(KEY);

        assertThat(otpService.verifyOtp(KEY, otp)).isFalse();
        assertThat(otpService.verifyOtp("reset:nobody", "123456")).isFalse();
    }

    @Test
    void nullOtpIsRejected() {
        otpService.generateOtp(KEY);

        assertThat(otpService.verifyOtp(KEY, null)).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
