package com.mudassir.eka.infrastructure.persistence.postgres.repository;

import com.mudassir.eka.infrastructure.persistence.postgres.entity.RefreshTokenEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    List<RefreshTokenEntity> findByUser(UserEntity user);

    @Modifying
    @Query("""
            UPDATE RefreshTokenEntity t
            SET t.revokedAt = :now
            WHERE t.user.id = :userId
              AND t.revokedAt IS NULL
            """)
    void revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.expiresAt < :cutoff")
    void deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
