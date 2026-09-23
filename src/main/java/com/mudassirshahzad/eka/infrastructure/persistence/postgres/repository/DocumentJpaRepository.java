package com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository;

import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.UserEntity;
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

    /**
     * Authorization Filter (P06.2): {@code d.classification} mapped to its numeric level exactly
     * like {@link com.mudassirshahzad.eka.domain.document.DocumentClassification#level()} — an
     * unrecognized or {@code NULL} value maps to
     * {@link com.mudassirshahzad.eka.domain.document.DocumentClassification#UNKNOWN_LEVEL}
     * (2147483647), which no real clearance can satisfy. This mapping must stay in sync with that
     * enum by hand — JPQL's {@code @Query} value must be a compile-time constant, so it cannot be
     * generated from the enum at runtime the way the BM25 adapter's equivalent SQL is.
     */
    @Query("""
            SELECT d FROM DocumentEntity d WHERE d.tenant = :tenant
            AND (CASE d.classification
                   WHEN 'PUBLIC' THEN 0 WHEN 'INTERNAL' THEN 1
                   WHEN 'CONFIDENTIAL' THEN 2 WHEN 'RESTRICTED' THEN 3
                   ELSE 2147483647 END) <= :maxLevel
            """)
    Page<DocumentEntity> findByTenantWithMaxClassificationLevel(
            @Param("tenant") TenantEntity tenant, @Param("maxLevel") int maxLevel, Pageable pageable);

    @Query("""
            SELECT d FROM DocumentEntity d WHERE d.owner = :owner AND d.tenant = :tenant
            AND (CASE d.classification
                   WHEN 'PUBLIC' THEN 0 WHEN 'INTERNAL' THEN 1
                   WHEN 'CONFIDENTIAL' THEN 2 WHEN 'RESTRICTED' THEN 3
                   ELSE 2147483647 END) <= :maxLevel
            """)
    Page<DocumentEntity> findByOwnerAndTenantWithMaxClassificationLevel(
            @Param("owner") UserEntity owner, @Param("tenant") TenantEntity tenant,
            @Param("maxLevel") int maxLevel, Pageable pageable);

    @Modifying
    @Query("UPDATE DocumentEntity d SET d.status = :status, d.updatedAt = :now WHERE d.id = :id")
    void updateStatus(@Param("id") UUID id,
                      @Param("status") String status,
                      @Param("now") Instant now);

    @Modifying
    @Query("UPDATE DocumentEntity d SET d.deletedAt = :now, d.updatedAt = :now WHERE d.id = :id")
    void softDeleteById(@Param("id") UUID id, @Param("now") Instant now);

    @Query("SELECT COUNT(d) FROM DocumentEntity d WHERE d.classification IS NULL")
    long countByClassificationIsNull();
}
