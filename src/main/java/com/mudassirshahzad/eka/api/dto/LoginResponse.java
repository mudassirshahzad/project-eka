package com.mudassirshahzad.eka.api.dto;

/**
 * An access token only — no refresh token is issued (ADR A02). {@code tokenType} is always
 * {@code "Bearer"}, spelled out rather than assumed so clients don't have to hardcode it.
 */
public record LoginResponse(String accessToken, String tokenType, long expiresInMs) {

    public static LoginResponse bearer(String accessToken, long expiresInMs) {
        return new LoginResponse(accessToken, "Bearer", expiresInMs);
    }
}
