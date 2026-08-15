package com.mudassirshahzad.eka.api.security;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Objects;

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

    public JwtAuthenticationToken(UserId userId, TenantId tenantId,
                                   Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.userId = Objects.requireNonNull(userId, "userId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        setAuthenticated(true);
    }

    public UserId userId() {
        return userId;
    }

    public TenantId tenantId() {
        return tenantId;
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
