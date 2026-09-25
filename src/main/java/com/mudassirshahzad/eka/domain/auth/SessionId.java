package com.mudassirshahzad.eka.domain.auth;

import java.util.UUID;

/**
 * Identifies one authenticated session — the link between a persisted {@link RefreshToken} and
 * every access token issued under it (carried as the {@code sid} claim).
 *
 * <p>Deliberately a separate identity from the refresh token's own secret value: the secret
 * rotates on every refresh (ADR RT02), while the session id stays stable for the life of the
 * session, so revoking a session does not depend on knowing which rotation is current.
 */
public record SessionId(UUID value) {

    public SessionId {
        if (value == null) throw new IllegalArgumentException("SessionId value must not be null");
    }

    public static SessionId generate() {
        return new SessionId(UUID.randomUUID());
    }

    public static SessionId of(UUID value) {
        return new SessionId(value);
    }

    public static SessionId of(String value) {
        return new SessionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
