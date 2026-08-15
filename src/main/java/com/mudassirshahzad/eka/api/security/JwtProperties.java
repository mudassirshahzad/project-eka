package com.mudassirshahzad.eka.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code security.jwt.*} (already scaffolded in {@code application.yml} ahead of this
 * milestone). {@code refresh-token-expiry-ms} is deliberately not bound here — refresh tokens
 * are out of scope for P05.2 (ADR A02).
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secretKey, long accessTokenExpiryMs) {}
