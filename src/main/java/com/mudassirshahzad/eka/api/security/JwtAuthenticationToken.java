package com.mudassirshahzad.eka.api.security;

import com.mudassirshahzad.eka.domain.auth.SessionId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The sole {@link org.springframework.security.core.Authentication} type this application ever
 * places in the {@code SecurityContext} (ADR A05) — carries exactly the identity a validated JWT
 * proves: which user, in which tenant, with which roles. There is no username/password variant
 * because there is no session-based login; every authenticated request arrives with a token
 * already validated by {@link JwtAuthenticationFilter}.
 */
public final class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final UserId userId;
    private final TenantId tenantId;
    private final SessionId sessionId;

    public JwtAuthenticationToken(UserId userId, TenantId tenantId,
                                   Collection<? extends GrantedAuthority> authorities,
                                   SessionId sessionId) {
        super(authorities);
        this.userId = Objects.requireNonNull(userId, "userId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        setAuthenticated(true);
    }

    public UserId userId() {
        return userId;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    /**
     * The session this token was issued under (WP-2, ADR RT01) — the handle {@code /auth/logout}
     * revokes, and the value {@link JwtAuthenticationFilter} re-validates on every request.
     */
    public SessionId sessionId() {
        return sessionId;
    }

    /**
     * The caller's roles, decoded from the {@code ROLE_*} authorities {@link JwtTokenProvider}
     * encodes (the exact format {@link AuthorizationInterceptor} already matches against). This
     * application is the sole minter of its own tokens, but decoding defensively (skipping, not
     * throwing on, an authority that isn't a recognized role) keeps a request from failing with an
     * unrelated 500 if that ever stops being true.
     */
    public Set<UserRole> roles() {
        Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
        for (GrantedAuthority authority : getAuthorities()) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                try {
                    roles.add(UserRole.valueOf(name.substring("ROLE_".length())));
                } catch (IllegalArgumentException ignored) {
                    // Not a recognized role — skip rather than fail the request.
                }
            }
        }
        return roles;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }
}
