package com.mudassir.eka.infrastructure.persistence.postgres.repository;

import com.mudassir.eka.infrastructure.persistence.postgres.entity.ConversationEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationJpaRepository extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity> findByIdAndUser(UUID id, UserEntity user);

    Page<ConversationEntity> findByUserAndTenant(UserEntity user, TenantEntity tenant, Pageable pageable);

    @Modifying
    @Query("UPDATE ConversationEntity c SET c.deletedAt = :now, c.updatedAt = :now WHERE c.id = :id")
    void softDeleteById(@Param("id") UUID id, @Param("now") Instant now);
}
