package com.Nhom2.booking.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window counters keyed by account or email (not by IP: requests arrive through Vercel and Railway proxies,
 * so the client IP is not trustworthy). In memory, so limits reset on restart and are per instance.
 */
@Component
public class RateLimiter {

    private static final long EVICT_INTERVAL_MS = 10 * 60_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Counts one attempt and returns true, or returns false without counting when the limit is already reached. */
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        boolean[] allowed = {false};
        windows.compute(key, (k, current) -> {
            Window active = activeOrNew(current, window);
            if (active.count() >= maxAttempts) {
                return active;
            }
            allowed[0] = true;
            return active.increment();
        });
        return allowed[0];
    }

    /** Counts one attempt unconditionally, e.g. a failed login. */
    public void record(String key, Duration window) {
        windows.compute(key, (k, current) -> activeOrNew(current, window).increment());
    }

    public boolean isLimited(String key, int maxAttempts) {
        Window current = windows.get(key);
        return current != null && !current.isExpired(clock.instant()) && current.count() >= maxAttempts;
    }

    public void reset(String key) {
        windows.remove(key);
    }

    // Keeps memory bounded: keys whose window has passed carry no information any more.
    @Scheduled(fixedDelay = EVICT_INTERVAL_MS)
    public void evictExpired() {
        Instant now = clock.instant();
        windows.values().removeIf(window -> window.isExpired(now));
    }

    int size() {
        return windows.size();
    }

    private Window activeOrNew(Window current, Duration window) {
        Instant now = clock.instant();
        return current == null || current.isExpired(now) ? new Window(now.plus(window), 0) : current;
    }

    private record Window(Instant expiresAt, int count) {
        Window increment() {
            return new Window(expiresAt, count + 1);
        }

        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
