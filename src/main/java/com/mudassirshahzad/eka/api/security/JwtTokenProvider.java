package com.mudassirshahzad.eka.api.security;

import com.mudassirshahzad.eka.domain.auth.SessionId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
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

    private static final String TENANT_CLAIM  = "tid";
    private static final String ROLES_CLAIM   = "roles";
    private static final String SESSION_CLAIM = "sid";

    private final JwtProperties properties;

    /**
     * Mints an access token bound to a session (WP-2, ADR RT01). The {@code sid} claim is what
     * makes this token revocable before its expiry: {@link JwtAuthenticationFilter} re-checks that
     * the session is still active on every request, so revoking the session invalidates every
     * access token issued under it.
     */
    public String generateAccessToken(UserId userId, TenantId tenantId, Set<UserRole> roles,
                                       SessionId sessionId) {
        Instant now = Instant.now();
        List<String> roleNames = roles.stream().map(Enum::name).toList();

        return Jwts.builder()
                .subject(userId.value().toString())
                .claim(TENANT_CLAIM, tenantId.value().toString())
                .claim(ROLES_CLAIM, roleNames)
                .claim(SESSION_CLAIM, sessionId.value().toString())
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

        // A token minted before WP-2 carries no sid. Rather than accept it as unrevocable, treat a
        // missing or unparseable sid as a malformed token: the filter already maps JwtException to
        // "no authentication" (ADR A04), and the alternative — honouring pre-WP-2 tokens — would
        // leave a 15-minute window of tokens that revocation cannot reach.
        String rawSessionId = claims.get(SESSION_CLAIM, String.class);
        if (rawSessionId == null || rawSessionId.isBlank()) {
            throw new MalformedJwtException("Access token carries no " + SESSION_CLAIM + " claim");
        }
        SessionId sessionId;
        try {
            sessionId = SessionId.of(rawSessionId);
        } catch (IllegalArgumentException ex) {
            throw new MalformedJwtException("Access token carries an unparseable " + SESSION_CLAIM + " claim");
        }

        return new JwtAuthenticationToken(userId, tenantId, authorities, sessionId);
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.secretKey().getBytes(StandardCharsets.UTF_8));
    }
}
