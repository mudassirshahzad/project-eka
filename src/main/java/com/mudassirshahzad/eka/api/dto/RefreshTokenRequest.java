package com.mudassirshahzad.eka.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Carries the refresh secret in the request body rather than an {@code Authorization} header: the
 * caller's access token has usually expired by the time they refresh, and overloading the bearer
 * header with a different kind of credential would make {@code JwtAuthenticationFilter}'s
 * contract ambiguous.
 */
public record RefreshTokenRequest(@NotBlank String refreshToken) {}
