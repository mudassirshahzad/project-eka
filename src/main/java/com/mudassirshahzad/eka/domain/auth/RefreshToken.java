package com.mudassirshahzad.eka.domain.auth;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * One authenticated session, identified by {@link SessionId} and authenticated by a rotating
 * opaque secret.
 *
 * <h3>The raw secret never enters this aggregate's state</h3>
 * <p>{@link #issue} accepts the raw token only to hash it; only the SHA-256 hash is ever stored or
 * compared. A database disclosure therefore yields no usable refresh token — the same reasoning
 * that puts BCrypt behind {@code User.passwordHash}, applied to the other long-lived credential in
 * this system. SHA-256 rather than BCrypt is deliberate and is not a weaker choice here: a refresh
 * token is 256 bits of {@code SecureRandom} output, so it has no low-entropy keyspace for a
 * password-cracking attack to search, and it is verified on a hot path where BCrypt's deliberate
 * slowness would be a denial-of-service vector rather than a defence.
 *
 * <h3>Revocation is what makes an access token killable</h3>
 * <p>Access tokens carry this session's {@link SessionId} as their {@code sid} claim. Because
 * every authenticated request re-checks that the session is still active, revoking a session
 * invalidates not just the refresh token but every access token issued under it — before its
 * natural expiry. That is the specific guarantee Phase 7 requires ("a compromised access token can
 * be revoked without waiting out its expiry"); a stateless JWT alone cannot provide it.
 */
public class RefreshToken {

    private final SessionId id;
    private final UserId    userId;
    private final TenantId  tenantId;
    private final String    tokenHash;
    private final Instant   issuedAt;
    private final Instant   expiresAt;
    private Instant         revokedAt;

    public static RefreshToken issue(
            SessionId id, UserId userId, TenantId tenantId,
            String rawToken, Instant issuedAt, Instant expiresAt
    ) {
        if (Objects.requireNonNull(rawToken, "rawToken").isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }
        if (!Objects.requireNonNull(expiresAt, "expiresAt")
                .isAfter(Objects.requireNonNull(issuedAt, "issuedAt"))) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        return new RefreshToken(id, userId, tenantId, hash(rawToken), issuedAt, expiresAt, null);
    }

    public static RefreshToken reconstitute(
            SessionId id, UserId userId, TenantId tenantId, String tokenHash,
            Instant issuedAt, Instant expiresAt, Instant revokedAt
    ) {
        return new RefreshToken(id, userId, tenantId, tokenHash, issuedAt, expiresAt, revokedAt);
    }

    private RefreshToken(
            SessionId id, UserId userId, TenantId tenantId, String tokenHash,
            Instant issuedAt, Instant expiresAt, Instant revokedAt
    ) {
        this.id        = Objects.requireNonNull(id, "id");
        this.userId    = Objects.requireNonNull(userId, "userId");
        this.tenantId  = Objects.requireNonNull(tenantId, "tenantId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.issuedAt  = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.revokedAt = revokedAt;
    }

    /** Hashes a raw token for storage or comparison — the only place this algorithm is chosen. */
    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java platform; absence means a broken JRE, not a runtime case.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Idempotent: revoking an already-revoked session keeps the original revocation time. */
    public void revoke(Instant when) {
        if (revokedAt == null) {
            this.revokedAt = Objects.requireNonNull(when, "when");
        }
    }

    public SessionId getId()       { return id; }
    public UserId getUserId()      { return userId; }
    public TenantId getTenantId()  { return tenantId; }
    public String getTokenHash()   { return tokenHash; }
    public Instant getIssuedAt()   { return issuedAt; }
    public Instant getExpiresAt()  { return expiresAt; }
    public Instant getRevokedAt()  { return revokedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RefreshToken other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
