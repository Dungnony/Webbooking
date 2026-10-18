package com.Nhom2.booking.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private MutableClock clock;
    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        limiter = new RateLimiter(clock);
    }

    @Test
    void allowsUpToTheLimitThenRefuses() {
        assertThat(limiter.tryAcquire("otp:a", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("otp:a", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("otp:a", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("otp:a", 3, WINDOW)).isFalse();
    }

    @Test
    void keysAreIndependent() {
        limiter.tryAcquire("otp:a", 1, WINDOW);

        assertThat(limiter.tryAcquire("otp:b", 1, WINDOW)).isTrue();
    }

    @Test
    void limitResetsAfterTheWindow() {
        limiter.tryAcquire("otp:a", 1, WINDOW);
        assertThat(limiter.tryAcquire("otp:a", 1, WINDOW)).isFalse();

        clock.advance(WINDOW.plusSeconds(1));

        assertThat(limiter.tryAcquire("otp:a", 1, WINDOW)).isTrue();
    }

    @Test
    void recordedFailuresLockOnlyOnceTheLimitIsReached() {
        for (int i = 0; i < 4; i++) {
            limiter.record("login:alice", WINDOW);
        }
        assertThat(limiter.isLimited("login:alice", 5)).isFalse();

        limiter.record("login:alice", WINDOW);

        assertThat(limiter.isLimited("login:alice", 5)).isTrue();
    }

    @Test
    void resetClearsTheCount() {
        for (int i = 0; i < 5; i++) {
            limiter.record("login:alice", WINDOW);
        }

        limiter.reset("login:alice");

        assertThat(limiter.isLimited("login:alice", 5)).isFalse();
    }

    @Test
    void expiredEntriesAreEvicted() {
        limiter.record("login:alice", WINDOW);
        clock.advance(WINDOW.plusSeconds(1));

        limiter.evictExpired();

        assertThat(limiter.size()).isZero();
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
