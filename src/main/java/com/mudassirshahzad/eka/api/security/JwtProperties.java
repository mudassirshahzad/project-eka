package com.mudassirshahzad.eka.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Binds {@code security.jwt.*}. {@code refresh-token-expiry-ms} — scaffolded in
 * {@code application.yml} since P05.2 but deliberately unbound while refresh tokens were out of
 * scope (ADR A02) — is bound and validated as of WP-2 (ADR RT01), so the property is no longer a
 * configuration value that looks live but controls nothing.
 *
 * <p>The compact constructor validates eagerly, at application-context startup (v0.6.1, ADR EX04)
 * — {@code @ConfigurationPropertiesScan} on {@code ProjectEkaApplication} means this record is
 * instantiated during context refresh regardless of when a request first needs it. Before this,
 * an HS256 key shorter than 256 bits would start the application successfully and only fail with
 * {@code io.jsonwebtoken.security.WeakKeyException} on the first token ever signed — i.e. the
 * first real login request, in production, observed by a user instead of an operator at boot.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secretKey, long accessTokenExpiryMs, long refreshTokenExpiryMs) {

    /** HS256 requires a key of at least 256 bits (RFC 7518 §3.2 / JJWT's {@code WeakKeyException}). */
    private static final int MIN_SECRET_KEY_BYTES = 32;

    public JwtProperties {
        Objects.requireNonNull(secretKey, "security.jwt.secret-key must be configured");

        int actualBytes = secretKey.getBytes(StandardCharsets.UTF_8).length;
        if (actualBytes < MIN_SECRET_KEY_BYTES) {
            throw new IllegalStateException(
                    "security.jwt.secret-key must be at least " + MIN_SECRET_KEY_BYTES
                    + " bytes for HS256, but the configured key is only " + actualBytes
                    + " bytes. Refusing to start rather than fail on the first login request.");
        }
        if (accessTokenExpiryMs <= 0) {
            throw new IllegalStateException(
                    "security.jwt.access-token-expiry-ms must be positive, but was "
                    + accessTokenExpiryMs);
        }
        if (refreshTokenExpiryMs <= 0) {
            throw new IllegalStateException(
                    "security.jwt.refresh-token-expiry-ms must be positive, but was "
                    + refreshTokenExpiryMs);
        }
        if (refreshTokenExpiryMs <= accessTokenExpiryMs) {
            throw new IllegalStateException(
                    "security.jwt.refresh-token-expiry-ms (" + refreshTokenExpiryMs
                    + ") must be longer than access-token-expiry-ms (" + accessTokenExpiryMs
                    + "); a refresh token that expires no later than the access token it renews "
                    + "can never actually renew anything.");
        }
    }
}
