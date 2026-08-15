package com.mudassirshahzad.eka.api.security;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET =
            "unit-test-secret-key-at-least-256-bits-long-0123456789abcdef";

    private final UserId   userId   = UserId.generate();
    private final TenantId tenantId = TenantId.generate();

    @Test
    void generateAccessToken_thenParseToken_roundTripsIdentityAndRoles() {
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, 900_000));

        String token = provider.generateAccessToken(userId, tenantId, EnumSet.of(UserRole.ADMIN, UserRole.USER));
        JwtAuthenticationToken parsed = provider.parseToken(token);

        assertThat(parsed.userId()).isEqualTo(userId);
        assertThat(parsed.tenantId()).isEqualTo(tenantId);
        assertThat(parsed.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        assertThat(parsed.isAuthenticated()).isTrue();
    }

    @Test
    void generateAccessToken_noRoles_producesEmptyAuthorities() {
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, 900_000));

        String token = provider.generateAccessToken(userId, tenantId, Set.of());
        JwtAuthenticationToken parsed = provider.parseToken(token);

        assertThat(parsed.getAuthorities()).isEmpty();
    }

    @Test
    void parseToken_expiredToken_throwsJwtException() {
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, -1));

        String expiredToken = provider.generateAccessToken(userId, tenantId, Set.of());

        assertThatThrownBy(() -> provider.parseToken(expiredToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseToken_tamperedSignature_throwsJwtException() {
        JwtTokenProvider issuer   = new JwtTokenProvider(new JwtProperties(SECRET, 900_000));
        JwtTokenProvider verifier = new JwtTokenProvider(
                new JwtProperties("a-completely-different-secret-key-also-256-bits-long-abc", 900_000));

        String token = issuer.generateAccessToken(userId, tenantId, Set.of());

        assertThatThrownBy(() -> verifier.parseToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseToken_malformedToken_throwsException() {
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, 900_000));

        assertThatThrownBy(() -> provider.parseToken("not-a-jwt")).isInstanceOf(RuntimeException.class);
    }
}
