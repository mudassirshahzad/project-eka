package com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository;

import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Revokes every still-active session for one user in a single statement (ADR RT03). Done as a
     * bulk update rather than load-mutate-save because reuse detection is a security response:
     * fewer round trips means a smaller window in which a stolen token can still be used.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE RefreshTokenEntity r
              SET r.revokedAt = :when
            WHERE r.userId = :userId
              AND r.revokedAt IS NULL
           """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("when") Instant when);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshTokenEntity r WHERE r.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
