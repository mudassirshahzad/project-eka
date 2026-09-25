package com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Extends {@link BaseUuidEntity} rather than {@link AuditableEntity}: a session row is written
 * once and mutated in exactly one way (revocation, which carries its own timestamp), so a general
 * {@code updated_at} column would be a second, weaker record of the same fact.
 *
 * <p>{@code userId}/{@code tenantId} are plain UUID columns rather than {@code @ManyToOne}
 * associations, unlike most entities here. Two reasons, both concrete: the domain aggregate holds
 * {@code UserId}/{@code TenantId} value objects and never navigates to a {@code User}, so an
 * association would buy nothing; and {@link UserEntity} carries
 * {@code @SQLRestriction("active = TRUE")}, which would make the association unresolvable for a
 * deactivated user's session — exactly the session most likely to need revoking or purging. The
 * foreign keys still exist and still cascade, declared in {@code V019} at the database level.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenEntity extends BaseUuidEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    // V009 declared this VARCHAR(255); a SHA-256 hex digest is always 64 chars, but the wider
    // existing column is left alone rather than churning the type of a table that already ships.
    @Column(name = "token_hash", nullable = false, updatable = false, length = 255)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant expiresAt;

    @Column(name = "revoked_at", columnDefinition = "TIMESTAMPTZ")
    private Instant revokedAt;
}
