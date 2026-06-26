package com.mudassir.eka.domain.user;

import com.mudassir.eka.domain.shared.TenantId;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public class User {

    private final UserId id;
    private final TenantId tenantId;
    private final String email;
    private String passwordHash;
    private boolean active;
    private final Set<UserRole> roles;
    private final Instant createdAt;
    private Instant updatedAt;

    public static User create(TenantId tenantId, String email, String passwordHash, Set<UserRole> roles) {
        User user = new User(UserId.generate(), tenantId, Instant.now());
        user.email        = Objects.requireNonNull(email, "email");
        user.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        user.active       = true;
        user.roles.addAll(Objects.requireNonNull(roles, "roles"));
        user.updatedAt    = user.createdAt;
        return user;
    }

    public static User reconstitute(
            UserId id, TenantId tenantId, String email,
            String passwordHash, boolean active, Set<UserRole> roles,
            Instant createdAt, Instant updatedAt
    ) {
        User user = new User(id, tenantId, createdAt);
        user.email        = email;
        user.passwordHash = passwordHash;
        user.active       = active;
        user.roles.addAll(roles);
        user.updatedAt    = updatedAt;
        return user;
    }

    private User(UserId id, TenantId tenantId, Instant createdAt) {
        this.id        = Objects.requireNonNull(id);
        this.tenantId  = Objects.requireNonNull(tenantId);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.roles     = EnumSet.noneOf(UserRole.class);
    }

    public void deactivate() {
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active    = true;
        this.updatedAt = Instant.now();
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
        this.updatedAt    = Instant.now();
    }

    public void addRole(UserRole role) {
        this.roles.add(role);
        this.updatedAt = Instant.now();
    }

    public void removeRole(UserRole role) {
        this.roles.remove(role);
        this.updatedAt = Instant.now();
    }

    public boolean hasRole(UserRole role) {
        return roles.contains(role);
    }

    public UserId getId()            { return id; }
    public TenantId getTenantId()    { return tenantId; }
    public String getEmail()         { return email; }
    public String getPasswordHash()  { return passwordHash; }
    public boolean isActive()        { return active; }
    public Set<UserRole> getRoles()  { return Collections.unmodifiableSet(roles); }
    public Instant getCreatedAt()    { return createdAt; }
    public Instant getUpdatedAt()    { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User u)) return false;
        return id.equals(u.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
