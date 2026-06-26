package com.mudassir.eka.infrastructure.persistence.postgres.repository;

import com.mudassir.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
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
public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findByIdAndTenant(UUID id, TenantEntity tenant);

    Page<DocumentEntity> findByTenant(TenantEntity tenant, Pageable pageable);

    Page<DocumentEntity> findByOwnerAndTenant(UserEntity owner, TenantEntity tenant, Pageable pageable);

    @Modifying
    @Query("UPDATE DocumentEntity d SET d.status = :status, d.updatedAt = :now WHERE d.id = :id")
    void updateStatus(@Param("id") UUID id,
                      @Param("status") String status,
                      @Param("now") Instant now);

    @Modifying
    @Query("UPDATE DocumentEntity d SET d.deletedAt = :now, d.updatedAt = :now WHERE d.id = :id")
    void softDeleteById(@Param("id") UUID id, @Param("now") Instant now);
}
