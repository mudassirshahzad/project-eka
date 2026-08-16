package com.mudassirshahzad.eka.api.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory, fixed-window request counter guarding {@code POST /api/v1/auth/login}
 * (v0.6.1, ADR EX05) — closes the "no rate limiting on login" gap from the post-Phase-5 audit.
 *
 * <p>Deliberately in-process, not distributed: this application runs as a single instance today
 * (no load balancer, no horizontal scaling — see {@code docker-compose.yml}), so a per-instance
 * {@link ConcurrentHashMap} is sufficient and adds no new infrastructure or dependency. If this
 * service is ever deployed behind a load balancer across multiple instances, each instance would
 * enforce its own independent limit — a real, accepted limitation of "simple, no distributed
 * infrastructure" as this milestone explicitly asked for, not an oversight; revisit with a shared
 * store (e.g. the already-present PostgreSQL, or Redis) only if that deployment shape happens.
 *
 * <p>Counts every request to the login endpoint regardless of outcome (not just failures) — the
 * simplest correct definition of "rate limit an endpoint," and avoids coupling this class to
 * authentication outcome. {@link #evictExpiredWindows()} bounds memory over a long-running
 * process; without it, a distinct IP would occupy one map entry forever.
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Window> windowsByKey = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxAttemptsPerWindow;

    @Autowired
    public LoginRateLimiter(@Value("${app.login-rate-limit.max-attempts-per-minute:10}") int maxAttemptsPerWindow) {
        this(Clock.systemUTC(), maxAttemptsPerWindow);
    }

    /** Package-private — lets tests supply a controllable {@link Clock}, mirroring the health
     *  indicators' dual-constructor pattern (P05.4, ADR OB05). */
    LoginRateLimiter(Clock clock, int maxAttemptsPerWindow) {
        this.clock = clock;
        this.maxAttemptsPerWindow = maxAttemptsPerWindow;
    }

    public void checkAllowed(String key) {
        Instant now = Instant.now(clock);

        Window window = windowsByKey.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (window.count().get() > maxAttemptsPerWindow) {
            log.warn("Login rate limit exceeded for key={}", key);
            throw new TooManyLoginAttemptsException("Too many login attempts. Try again later.");
        }
    }

    @Scheduled(fixedRate = 5, initialDelay = 5, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void evictExpiredWindows() {
        Instant now = Instant.now(clock);
        windowsByKey.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private record Window(Instant start, AtomicInteger count) {
        boolean isExpired(Instant now) {
            return start.plus(WINDOW).isBefore(now);
        }
    }
}
