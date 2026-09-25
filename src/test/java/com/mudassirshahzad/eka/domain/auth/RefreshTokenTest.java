package com.mudassirshahzad.eka.domain.auth;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenTest {

    private final SessionId sessionId = SessionId.generate();
    private final UserId    userId    = UserId.generate();
    private final TenantId  tenantId  = TenantId.generate();
    private final Instant   now       = Instant.parse("2026-09-25T10:00:00Z");

    private RefreshToken issue(Instant issuedAt, Instant expiresAt) {
        return RefreshToken.issue(sessionId, userId, tenantId, "raw-secret", issuedAt, expiresAt);
    }

    @Test
    void issue_storesOnlyTheHash_neverTheRawSecret() {
        RefreshToken token = issue(now, now.plus(7, ChronoUnit.DAYS));

        assertThat(token.getTokenHash())
                .isEqualTo(RefreshToken.hash("raw-secret"))
                .isNotEqualTo("raw-secret")
                .hasSize(64);
    }

    @Test
    void hash_isDeterministicAndDistinguishesDifferentSecrets() {
        assertThat(RefreshToken.hash("secret-a")).isEqualTo(RefreshToken.hash("secret-a"));
        assertThat(RefreshToken.hash("secret-a")).isNotEqualTo(RefreshToken.hash("secret-b"));
    }

    @Test
    void issue_blankOrNullRawToken_isRejected() {
        Instant expiry = now.plus(7, ChronoUnit.DAYS);

        assertThatThrownBy(() -> RefreshToken.issue(sessionId, userId, tenantId, "  ", now, expiry))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(sessionId, userId, tenantId, null, now, expiry))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void issue_expiryNotAfterIssuance_isRejected() {
        assertThatThrownBy(() -> issue(now, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt must be after issuedAt");
    }

    @Test
    void freshToken_isActive_untilItExpires() {
        RefreshToken token = issue(now, now.plus(1, ChronoUnit.HOURS));

        assertThat(token.isActive(now)).isTrue();
        assertThat(token.isActive(now.plus(59, ChronoUnit.MINUTES))).isTrue();
        assertThat(token.isActive(now.plus(1, ChronoUnit.HOURS))).isFalse();
        assertThat(token.isActive(now.plus(2, ChronoUnit.HOURS))).isFalse();
    }

    @Test
    void revoke_makesTokenInactiveImmediately_evenBeforeExpiry() {
        RefreshToken token = issue(now, now.plus(7, ChronoUnit.DAYS));

        token.revoke(now.plus(1, ChronoUnit.MINUTES));

        assertThat(token.isRevoked()).isTrue();
        // This is the property that makes a leaked access token killable: the session dies now,
        // not when the token would otherwise have expired six days later.
        assertThat(token.isActive(now.plus(2, ChronoUnit.MINUTES))).isFalse();
    }

    @Test
    void revoke_isIdempotent_keepingTheOriginalRevocationTime() {
        RefreshToken token = issue(now, now.plus(7, ChronoUnit.DAYS));
        Instant first = now.plus(1, ChronoUnit.MINUTES);

        token.revoke(first);
        token.revoke(now.plus(30, ChronoUnit.MINUTES));

        assertThat(token.getRevokedAt()).isEqualTo(first);
    }

    @Test
    void reconstitute_restoresRevokedStateWithoutRehashing() {
        Instant revokedAt = now.plus(5, ChronoUnit.MINUTES);
        RefreshToken token = RefreshToken.reconstitute(
                sessionId, userId, tenantId, "stored-hash", now, now.plus(7, ChronoUnit.DAYS), revokedAt);

        assertThat(token.getTokenHash()).isEqualTo("stored-hash");
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.isActive(now.plus(10, ChronoUnit.MINUTES))).isFalse();
    }

    @Test
    void identity_isTheSessionId() {
        RefreshToken a = issue(now, now.plus(7, ChronoUnit.DAYS));
        RefreshToken b = RefreshToken.reconstitute(
                sessionId, UserId.generate(), TenantId.generate(), "other-hash",
                now, now.plus(1, ChronoUnit.DAYS), null);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
