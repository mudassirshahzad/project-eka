package com.mudassirshahzad.eka.api.security;

/**
 * Thrown by {@link LoginRateLimiter} when a caller exceeds the allowed login attempts within the
 * current window (v0.6.1, ADR EX05). Mapped to {@code 429 Too Many Requests} by
 * {@code GlobalExceptionHandler}.
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException(String message) {
        super(message);
    }
}
