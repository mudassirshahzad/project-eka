package com.mudassirshahzad.eka.domain.auth;

import com.mudassirshahzad.eka.domain.user.UserId;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findById(SessionId id);

    /** Looks a session up by the SHA-256 hash of its presented secret — never by the raw value. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes every still-active session belonging to one user, returning how many were revoked.
     * Used for refresh-token reuse detection (ADR RT03), where the safe response to a replayed
     * secret is to end every session that user holds, not just the replayed one.
     */
    int revokeAllForUser(UserId userId, Instant when);

    /** Deletes sessions that expired before the given instant; returns how many rows were removed. */
    int deleteExpiredBefore(Instant cutoff);
}
