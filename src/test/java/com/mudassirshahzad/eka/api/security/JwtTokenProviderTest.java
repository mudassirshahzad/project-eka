package com.mudassirshahzad.eka.api.security;

import com.mudassirshahzad.eka.domain.auth.SessionId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET =
            "unit-test-secret-key-at-least-256-bits-long-0123456789abcdef";

    private final UserId    userId    = UserId.generate();
    private final TenantId  tenantId  = TenantId.generate();
    private final SessionId sessionId = SessionId.generate();

    @Test
    void generateAccessToken_thenParseToken_roundTripsIdentityAndRoles() {
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, 900_000, 604_800_000));

        String token = provider.generateAccessToken(userId, tenantId, EnumSet.of(UserRole.ADMIN, UserRole.USER), sessionId);
        JwtAuthenticationToken parsed = provider.parseToken(token);

        assertThat(parsed.userId()).isEqualTo(userId);
        assertThat(parsed.tenantId()).isEqualTo(tenantId);
        assertThat(parsed.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        assertThat(parsed.isAuthenticated()).isTrue();
        assertThat(parsed.sessionId()).isEqualTo(sessionId);
    }

    @Test
    void parseToken_tokenWithoutSessionClaim_isRejectedAsMalformed() {
        // A token minted before WP-2 carries no sid and therefore could never be revoked.
        // Honouring it would leave a window of unrevocable tokens, so it is refused outright.
        JwtProperties properties = new JwtProperties(SECRET, 900_000, 604_800_000);
        String legacyToken = io.jsonwebtoken.Jwts.builder()
                .subject(userId.value().toString())
                .claim("tid", tenantId.value().toString())
                .claim("roles", java.util.List.of("USER"))
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 900_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)), io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> new JwtTokenProvider(properties).parseToken(legacyToken))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void generateAccessToken_noRoles_producesEmptyAuthorities() {
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, 900_000, 604_800_000));

        String token = provider.generateAccessToken(userId, tenantId, Set.of(), sessionId);
        JwtAuthenticationToken parsed = provider.parseToken(token);

        assertThat(parsed.getAuthorities()).isEmpty();
    }

    /**
     * v0.6.1, ADR EX04 made {@code accessTokenExpiryMs} reject non-positive values at
     * construction, so an already-expired token can no longer be produced via
     * {@code JwtTokenProvider.generateAccessToken} the way this test did before. Instead, builds
     * the token directly with an already-past {@code exp} claim, signed with the same key
     * {@link JwtTokenProvider} would verify against — still exercising real parse/verify logic,
     * not a mock.
     */
    @Test
    void parseToken_expiredToken_throwsJwtException() {
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, 900_000, 604_800_000));

        String expiredToken = Jwts.builder()
                .subject(userId.value().toString())
                .expiration(Date.from(Instant.now().minusSeconds(10)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> provider.parseToken(expiredToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseToken_tamperedSignature_throwsJwtException() {
        JwtTokenProvider issuer   = new JwtTokenProvider(new JwtProperties(SECRET, 900_000, 604_800_000));
        JwtTokenProvider verifier = new JwtTokenProvider(
                new JwtProperties("a-completely-different-secret-key-also-256-bits-long-abc", 900_000, 604_800_000));

        String token = issuer.generateAccessToken(userId, tenantId, Set.of(), sessionId);

        assertThatThrownBy(() -> verifier.parseToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseToken_malformedToken_throwsException() {
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, 900_000, 604_800_000));

        assertThatThrownBy(() -> provider.parseToken("not-a-jwt")).isInstanceOf(RuntimeException.class);
    }
}
