package com.Nhom2.booking.service;

import com.Nhom2.booking.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sign-ups waiting for their email OTP. Kept in memory on purpose: the OTP itself lives in memory for
 * {@link OtpService#OTP_TTL}, so after a restart the user has to request a new code anyway.
 * Entries expire with the OTP and the total is capped, so sign-up spam cannot grow the heap without bound.
 */
@Component
public class PendingRegistrations {

    static final int MAX_PENDING = 10_000;

    /** {@code passwordHash} is already BCrypt-encoded: the raw password is never kept. */
    public record Registration(String username, String email, String passwordHash) {
    }

    private record Entry(Registration registration, Instant expiresAt) {
    }

    private final Map<String, Entry> byEmail = new ConcurrentHashMap<>();
    private final Clock clock;

    public PendingRegistrations(Clock clock) {
        this.clock = clock;
    }

    /** Replaces any earlier sign-up for the same email. */
    public void put(Registration registration) {
        Instant now = clock.instant();
        byEmail.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
        if (byEmail.size() >= MAX_PENDING && !byEmail.containsKey(registration.email())) {
            throw new TooManyRequestsException("error.registration.busy");
        }
        byEmail.put(registration.email(), new Entry(registration, now.plus(OtpService.OTP_TTL)));
    }

    /** Removes and returns the sign-up atomically, so two concurrent confirmations cannot both create the account. */
    public Optional<Registration> take(String email) {
        Entry entry = byEmail.remove(email);
        if (entry == null || !clock.instant().isBefore(entry.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(entry.registration());
    }
}
