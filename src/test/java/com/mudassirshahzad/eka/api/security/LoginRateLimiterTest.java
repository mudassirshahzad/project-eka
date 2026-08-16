package com.mudassirshahzad.eka.api.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * v0.6.1, ADR EX05: fixed-window counter behavior, verified with a controllable {@link Clock}
 * rather than real sleeps.
 */
class LoginRateLimiterTest {

    private static final int MAX_ATTEMPTS = 3;

    @Test
    void checkAllowed_underLimit_neverThrows() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock, MAX_ATTEMPTS);

        assertThatCode(() -> {
            limiter.checkAllowed("1.2.3.4");
            limiter.checkAllowed("1.2.3.4");
            limiter.checkAllowed("1.2.3.4");
        }).doesNotThrowAnyException();
    }

    @Test
    void checkAllowed_exceedsLimitWithinWindow_throws() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock, MAX_ATTEMPTS);

        limiter.checkAllowed("1.2.3.4");
        limiter.checkAllowed("1.2.3.4");
        limiter.checkAllowed("1.2.3.4");

        assertThatThrownBy(() -> limiter.checkAllowed("1.2.3.4"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void checkAllowed_differentKeys_trackedIndependently() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock, MAX_ATTEMPTS);

        limiter.checkAllowed("1.2.3.4");
        limiter.checkAllowed("1.2.3.4");
        limiter.checkAllowed("1.2.3.4");

        assertThatCode(() -> limiter.checkAllowed("5.6.7.8")).doesNotThrowAnyException();
    }

    @Test
    void checkAllowed_afterWindowElapses_resetsCount() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock, MAX_ATTEMPTS);

        limiter.checkAllowed("1.2.3.4");
        limiter.checkAllowed("1.2.3.4");
        limiter.checkAllowed("1.2.3.4");

        clock.advance(Duration.ofMinutes(2));

        assertThatCode(() -> limiter.checkAllowed("1.2.3.4")).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
