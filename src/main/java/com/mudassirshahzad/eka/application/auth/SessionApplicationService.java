package com.mudassirshahzad.eka.application.auth;

import com.mudassirshahzad.eka.application.shared.InvalidCredentialsException;
import com.mudassirshahzad.eka.domain.auth.RefreshToken;
import com.mudassirshahzad.eka.domain.auth.RefreshTokenRepository;
import com.mudassirshahzad.eka.domain.auth.SessionId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.User;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRepository;
import com.mudassirshahzad.eka.domain.user.UserRole;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Owns the session lifecycle: issue on login, rotate on refresh, revoke on logout, and answer
 * "is this session still active" for every authenticated request.
 *
 * <p>Deliberately mints no JWT. ADR A03 established that the application layer decides identity
 * questions while only {@code api.security.JwtTokenProvider} encodes the outcome as a token, and
 * that split is what keeps this service (and the ArchUnit rule behind it) free of any dependency
 * on the {@code api} layer. What it returns instead is an {@link IssuedSession} — the caller mints
 * the access token from it.
 */
@Slf4j
@Service
public class SessionApplicationService {

    /** 256 bits of SecureRandom, URL-safe Base64 encoded — no ambiguity, no padding. */
    private static final int RAW_TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository         userRepository;
    private final MeterRegistry          meterRegistry;
    private final Clock                  clock;
    private final long                   refreshTokenExpiryMs;
    private final SecureRandom           secureRandom = new SecureRandom();

    @Autowired
    public SessionApplicationService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            MeterRegistry meterRegistry,
            @Value("${security.jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs
    ) {
        this(refreshTokenRepository, userRepository, meterRegistry, Clock.systemUTC(), refreshTokenExpiryMs);
    }

    /** Test seam for a controllable clock — mirrors {@code LoginRateLimiter}'s existing pattern. */
    SessionApplicationService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            MeterRegistry meterRegistry,
            Clock clock,
            long refreshTokenExpiryMs
    ) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository");
        this.userRepository         = Objects.requireNonNull(userRepository, "userRepository");
        this.meterRegistry          = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.clock                  = Objects.requireNonNull(clock, "clock");
        if (refreshTokenExpiryMs <= 0) {
            throw new IllegalStateException(
                    "security.jwt.refresh-token-expiry-ms must be positive, but was " + refreshTokenExpiryMs);
        }
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    /** Issues a brand-new session for an already-authenticated user. */
    @Transactional
    public IssuedSession issue(User user) {
        Objects.requireNonNull(user, "user");
        return persistNewSession(user.getId(), user.getTenantId(), user.getRoles());
    }

    /**
     * Validates and rotates a presented refresh token (ADR RT02): the presented session is revoked
     * and a fresh one issued, so a refresh secret is single-use.
     *
     * @throws InvalidCredentialsException if the token is unknown, expired, or already revoked —
     *         the same opaque failure every other credential check in this codebase produces
     *         (ADR A06/OW01), so a caller cannot distinguish "never existed" from "already used"
     */
    // noRollbackFor is load-bearing, not a style choice: reuse detection below revokes every
    // session the user holds and then throws to reject the caller. Under the default rollback
    // rules that throw would roll the revocation back, so detecting a stolen token would revoke
    // nothing at all. Caught by this work package's own end-to-end test, which asserted the
    // legitimate session was dead afterwards and found it still live.
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public IssuedSession rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidCredentialsException();
        }

        Instant now = clock.instant();
        RefreshToken presented = refreshTokenRepository
                .findByTokenHash(RefreshToken.hash(rawRefreshToken))
                .orElseThrow(() -> {
                    meterRegistry.counter("eka.auth.failures", "type", "refresh").increment();
                    return new InvalidCredentialsException();
                });

        // Reuse detection (ADR RT03): a secret that was already rotated away is either a replay of
        // a stolen token or a stolen token being raced against the legitimate holder. Either way the
        // safe response is to end every session this user holds, not just this one.
        if (presented.isRevoked()) {
            int revoked = refreshTokenRepository.revokeAllForUser(presented.getUserId(), now);
            meterRegistry.counter("eka.auth.refresh.reuse").increment();
            log.warn("Refresh token reuse detected; revoked {} session(s) for the owning user", revoked);
            throw new InvalidCredentialsException();
        }

        if (!presented.isActive(now)) {
            meterRegistry.counter("eka.auth.failures", "type", "refresh").increment();
            throw new InvalidCredentialsException();
        }

        // Roles and active-flag are re-read rather than carried forward: a session must not outlive
        // the user's right to hold it. A deactivated user's refresh attempt fails here even though
        // their refresh token is still technically unexpired.
        User user = userRepository.findById(presented.getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> {
                    meterRegistry.counter("eka.auth.failures", "type", "refresh").increment();
                    return new InvalidCredentialsException();
                });

        presented.revoke(now);
        refreshTokenRepository.save(presented);

        return persistNewSession(user.getId(), user.getTenantId(), user.getRoles());
    }

    /**
     * Revokes one session. Idempotent and deliberately silent about whether the session existed —
     * logout must not become an oracle for guessing valid session ids.
     */
    @Transactional
    public void revoke(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        refreshTokenRepository.findById(sessionId).ifPresent(session -> {
            session.revoke(clock.instant());
            refreshTokenRepository.save(session);
        });
    }

    /**
     * The check that makes an access token revocable before its expiry — consulted on every
     * authenticated request by {@code JwtAuthenticationFilter}.
     */
    @Transactional(readOnly = true)
    public boolean isSessionActive(SessionId sessionId) {
        if (sessionId == null) return false;
        Instant now = clock.instant();
        return refreshTokenRepository.findById(sessionId)
                .map(session -> session.isActive(now))
                .orElse(false);
    }

    /** Removes sessions that have already expired; returns how many rows were deleted. */
    @Transactional
    public int purgeExpiredSessions() {
        return refreshTokenRepository.deleteExpiredBefore(clock.instant());
    }

    private IssuedSession persistNewSession(UserId userId, TenantId tenantId, Set<UserRole> roles) {
        Instant   now       = clock.instant();
        SessionId sessionId = SessionId.generate();
        String    rawToken  = generateRawToken();

        refreshTokenRepository.save(RefreshToken.issue(
                sessionId, userId, tenantId, rawToken, now, now.plusMillis(refreshTokenExpiryMs)));

        return new IssuedSession(sessionId, rawToken, userId, tenantId, roles, refreshTokenExpiryMs);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Exposed for the rare caller that needs the configured lifetime without re-reading config. */
    public long refreshTokenExpiryMs() {
        return refreshTokenExpiryMs;
    }

    /** Package-visible seam for tests that need to assert on a stored session. */
    Optional<RefreshToken> findSession(SessionId sessionId) {
        return refreshTokenRepository.findById(sessionId);
    }
}
