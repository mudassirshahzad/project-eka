package com.mudassirshahzad.eka.api.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * v0.6.1, ADR EX04: {@link JwtProperties}'s compact constructor must reject a weak HS256 secret
 * (and a nonsensical expiry) eagerly, at construction time — proving the application fails fast
 * at startup rather than on the first login request.
 */
class JwtPropertiesTest {

    @Test
    void construct_secretKeyBelow32Bytes_throwsImmediately() {
        assertThatThrownBy(() -> new JwtProperties("too-short-key", 900_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void construct_secretKeyExactly32Bytes_succeeds() {
        String exactly32Bytes = "a".repeat(32);
        JwtProperties properties = new JwtProperties(exactly32Bytes, 900_000);

        assertThat(properties.secretKey()).isEqualTo(exactly32Bytes);
    }

    @Test
    void construct_nullSecretKey_throwsImmediately() {
        assertThatThrownBy(() -> new JwtProperties(null, 900_000))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void construct_zeroExpiry_throwsImmediately() {
        String validKey = "a".repeat(32);
        assertThatThrownBy(() -> new JwtProperties(validKey, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void construct_negativeExpiry_throwsImmediately() {
        String validKey = "a".repeat(32);
        assertThatThrownBy(() -> new JwtProperties(validKey, -1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be positive");
    }
}
