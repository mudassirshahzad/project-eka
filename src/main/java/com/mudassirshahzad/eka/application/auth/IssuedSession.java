package com.mudassirshahzad.eka.application.auth;

import com.mudassirshahzad.eka.domain.auth.SessionId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;

import java.util.Set;

/**
 * A newly created or rotated session, returned to the caller that must now mint an access token
 * for it.
 *
 * <p>This is the one place the raw refresh-token secret exists outside the HTTP response — it is
 * never persisted (only its hash is) and never logged. The identity fields travel alongside it so
 * that {@code AuthController} can mint the access token without a second user lookup, preserving
 * ADR A03: the application layer decides that a session is valid, and only {@code api.security}
 * turns that decision into a JWT.
 */
public record IssuedSession(
        SessionId sessionId,
        String rawRefreshToken,
        UserId userId,
        TenantId tenantId,
        Set<UserRole> roles,
        long refreshTokenExpiresInMs
) {}
