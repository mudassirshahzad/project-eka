package com.mudassirshahzad.eka.api.security;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Issues and verifies HS256 access tokens (ADR A01). {@code security.jwt.secret-key} was already
 * scaffolded as a single symmetric key in {@code application.yml} ahead of this milestone, so
 * HMAC-SHA256 needs no additional key-management infrastructure (no PEM key pair, no JWKS
 * endpoint) that an asymmetric scheme would require — appropriate for a single monolith that both
 * signs and verifies its own tokens.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String TENANT_CLAIM = "tid";
    private static final String ROLES_CLAIM  = "roles";

    private final JwtProperties properties;

    public String generateAccessToken(UserId userId, TenantId tenantId, Set<UserRole> roles) {
        Instant now = Instant.now();
        List<String> roleNames = roles.stream().map(Enum::name).toList();

        return Jwts.builder()
                .subject(userId.value().toString())
                .claim(TENANT_CLAIM, tenantId.value().toString())
                .claim(ROLES_CLAIM, roleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(properties.accessTokenExpiryMs())))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * @throws JwtException if the token is malformed, expired, or fails signature verification —
     *                       callers never see raw parsing failures; {@link JwtAuthenticationFilter}
     *                       is the sole caller and treats every {@link JwtException} identically
     *                       (ADR A04)
     */
    public JwtAuthenticationToken parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UserId userId = UserId.of(claims.getSubject());
        TenantId tenantId = TenantId.of(claims.get(TENANT_CLAIM, String.class));

        List<?> rawRoles = claims.get(ROLES_CLAIM, List.class);
        List<GrantedAuthority> authorities = rawRoles == null
                ? List.of()
                : rawRoles.stream()
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

        return new JwtAuthenticationToken(userId, tenantId, authorities);
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.secretKey().getBytes(StandardCharsets.UTF_8));
    }
}
