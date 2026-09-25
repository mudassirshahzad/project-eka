package com.mudassirshahzad.eka.api.dto;

/**
 * An access token plus the refresh token that can renew it (WP-2, ADR RT01 — superseding ADR A02's
 * access-token-only shape). {@code tokenType} is always {@code "Bearer"}, spelled out rather than
 * assumed so clients don't have to hardcode it.
 *
 * <p>The refresh token is returned exactly once per rotation and is never recoverable afterwards —
 * only its hash is stored (ADR RT04). A client that loses it must log in again.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        String refreshToken,
        long refreshExpiresInMs
) {

    public static LoginResponse bearer(String accessToken, long expiresInMs,
                                        String refreshToken, long refreshExpiresInMs) {
        return new LoginResponse(accessToken, "Bearer", expiresInMs, refreshToken, refreshExpiresInMs);
    }
}
