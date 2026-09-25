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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionApplicationServiceTest {

    private static final long REFRESH_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository         userRepository;

    private SimpleMeterRegistry       meterRegistry;
    private SessionApplicationService service;
    private Instant                   now;
    private User                      user;

    @BeforeEach
    void setUp() {
        now           = Instant.parse("2026-09-25T10:00:00Z");
        meterRegistry = new SimpleMeterRegistry();
        service       = new SessionApplicationService(
                refreshTokenRepository, userRepository, meterRegistry,
                Clock.fixed(now, ZoneOffset.UTC), REFRESH_TTL_MS);
        user = User.create(TenantId.generate(), "user@example.com", "hash", EnumSet.of(UserRole.USER));
    }

    private RefreshToken storedSession(String rawSecret, Instant issuedAt, Instant expiresAt) {
        return RefreshToken.issue(
                SessionId.generate(), user.getId(), user.getTenantId(), rawSecret, issuedAt, expiresAt);
    }

    @Test
    void issue_persistsASessionAndReturnsTheRawSecretExactlyOnce() {
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IssuedSession issued = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());

        assertThat(issued.rawRefreshToken()).isNotBlank();
        assertThat(issued.userId()).isEqualTo(user.getId());
        assertThat(issued.tenantId()).isEqualTo(user.getTenantId());
        assertThat(issued.refreshTokenExpiresInMs()).isEqualTo(REFRESH_TTL_MS);
        // The persisted row carries only the hash — the raw secret exists solely in the response.
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(RefreshToken.hash(issued.rawRefreshToken()));
        assertThat(saved.getValue().getId()).isEqualTo(issued.sessionId());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(now.plusMillis(REFRESH_TTL_MS));
    }

    @Test
    void issue_producesADistinctSecretEveryTime() {
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.issue(user).rawRefreshToken())
                .isNotEqualTo(service.issue(user).rawRefreshToken());
    }

    @Test
    void rotate_validToken_revokesTheOldSessionAndIssuesANewOne() {
        RefreshToken existing = storedSession("old-secret", now.minus(1, ChronoUnit.HOURS),
                now.plus(6, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByTokenHash(RefreshToken.hash("old-secret")))
                .thenReturn(Optional.of(existing));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IssuedSession rotated = service.rotate("old-secret");

        assertThat(existing.isRevoked()).isTrue();
        assertThat(rotated.sessionId()).isNotEqualTo(existing.getId());
        assertThat(rotated.rawRefreshToken()).isNotEqualTo("old-secret");
        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void rotate_unknownToken_isRejectedOpaquely() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("never-issued"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(meterRegistry.get("eka.auth.failures").tag("type", "refresh").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void rotate_blankOrNullToken_isRejectedWithoutTouchingTheRepository() {
        assertThatThrownBy(() -> service.rotate("  ")).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.rotate(null)).isInstanceOf(InvalidCredentialsException.class);

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void rotate_expiredToken_isRejected() {
        RefreshToken expired = storedSession("stale", now.minus(8, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByTokenHash(RefreshToken.hash("stale")))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("stale"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void rotate_replayedToken_revokesEverySessionTheUserHolds() {
        RefreshToken alreadyRotated = storedSession("stolen", now.minus(1, ChronoUnit.HOURS),
                now.plus(6, ChronoUnit.DAYS));
        alreadyRotated.revoke(now.minus(30, ChronoUnit.MINUTES));
        when(refreshTokenRepository.findByTokenHash(RefreshToken.hash("stolen")))
                .thenReturn(Optional.of(alreadyRotated));
        when(refreshTokenRepository.revokeAllForUser(user.getId(), now)).thenReturn(3);

        assertThatThrownBy(() -> service.rotate("stolen"))
                .isInstanceOf(InvalidCredentialsException.class);

        // Reuse of an already-rotated secret means it leaked: ending every session is the safe
        // response, not just refusing this one request.
        verify(refreshTokenRepository).revokeAllForUser(user.getId(), now);
        assertThat(meterRegistry.get("eka.auth.refresh.reuse").counter().count()).isEqualTo(1.0);
    }

    @Test
    void rotate_deactivatedUser_isRejectedEvenWithAnUnexpiredToken() {
        RefreshToken valid = storedSession("valid", now.minus(1, ChronoUnit.HOURS),
                now.plus(6, ChronoUnit.DAYS));
        user.deactivate();
        when(refreshTokenRepository.findByTokenHash(RefreshToken.hash("valid")))
                .thenReturn(Optional.of(valid));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.rotate("valid"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void revoke_marksTheSessionRevoked() {
        RefreshToken session = storedSession("secret", now.minus(1, ChronoUnit.HOURS),
                now.plus(6, ChronoUnit.DAYS));
        when(refreshTokenRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.revoke(session.getId());

        assertThat(session.isRevoked()).isTrue();
        assertThat(session.getRevokedAt()).isEqualTo(now);
    }

    @Test
    void revoke_unknownSession_isSilentlyIgnored() {
        SessionId unknown = SessionId.generate();
        when(refreshTokenRepository.findById(unknown)).thenReturn(Optional.empty());

        service.revoke(unknown);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void isSessionActive_reflectsRevocationAndExpiry() {
        RefreshToken active = storedSession("a", now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        RefreshToken revoked = storedSession("b", now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        revoked.revoke(now.minus(1, ChronoUnit.MINUTES));
        RefreshToken expired = storedSession("c", now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));

        when(refreshTokenRepository.findById(active.getId())).thenReturn(Optional.of(active));
        when(refreshTokenRepository.findById(revoked.getId())).thenReturn(Optional.of(revoked));
        when(refreshTokenRepository.findById(expired.getId())).thenReturn(Optional.of(expired));

        assertThat(service.isSessionActive(active.getId())).isTrue();
        assertThat(service.isSessionActive(revoked.getId())).isFalse();
        assertThat(service.isSessionActive(expired.getId())).isFalse();
    }

    @Test
    void isSessionActive_unknownOrNullSession_isFalse_failingClosed() {
        SessionId unknown = SessionId.generate();
        when(refreshTokenRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThat(service.isSessionActive(unknown)).isFalse();
        assertThat(service.isSessionActive(null)).isFalse();
    }

    @Test
    void purgeExpiredSessions_delegatesToTheRepositoryWithTheCurrentInstant() {
        when(refreshTokenRepository.deleteExpiredBefore(now)).thenReturn(4);

        assertThat(service.purgeExpiredSessions()).isEqualTo(4);
        verify(refreshTokenRepository).deleteExpiredBefore(now);
    }

    @Test
    void constructor_rejectsANonPositiveRefreshTtl() {
        assertThatThrownBy(() -> new SessionApplicationService(
                refreshTokenRepository, userRepository, meterRegistry, Clock.systemUTC(), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refresh-token-expiry-ms");
    }
}
