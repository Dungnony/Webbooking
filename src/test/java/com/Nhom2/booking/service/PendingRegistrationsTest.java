package com.Nhom2.booking.service;

import com.Nhom2.booking.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendingRegistrationsTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-03-10T08:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final PendingRegistrations pending = new PendingRegistrations(clock);

    @Test
    void aRegistrationCanBeTakenOnlyOnce() {
        pending.put(registration("carol@example.com"));

        assertThat(pending.take("carol@example.com")).map(PendingRegistrations.Registration::username).contains("carol");
        assertThat(pending.take("carol@example.com")).isEmpty();
    }

    @Test
    void registrationExpiresTogetherWithItsOtp() {
        pending.put(registration("carol@example.com"));

        now.set(now.get().plus(OtpService.OTP_TTL));

        assertThat(pending.take("carol@example.com")).isEmpty();
    }

    @Test
    void newSignUpsAreRefusedWhenTheStoreIsFull() {
        for (int i = 0; i < PendingRegistrations.MAX_PENDING; i++) {
            pending.put(registration("user" + i + "@example.com"));
        }

        assertThatThrownBy(() -> pending.put(registration("late@example.com")))
                .isInstanceOf(TooManyRequestsException.class);
        // Re-sending the code for an address already waiting is still allowed.
        pending.put(registration("user0@example.com"));
    }

    @Test
    void expiredSignUpsFreeTheirSlot() {
        for (int i = 0; i < PendingRegistrations.MAX_PENDING; i++) {
            pending.put(registration("user" + i + "@example.com"));
        }
        now.set(now.get().plus(OtpService.OTP_TTL));

        pending.put(registration("late@example.com"));

        assertThat(pending.take("late@example.com")).isPresent();
    }

    private static PendingRegistrations.Registration registration(String email) {
        return new PendingRegistrations.Registration(email.substring(0, email.indexOf('@')), email, "$2a$hash");
    }
}
