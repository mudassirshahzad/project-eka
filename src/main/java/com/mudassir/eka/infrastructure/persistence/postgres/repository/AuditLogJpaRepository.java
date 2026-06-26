package com.mudassir.eka.infrastructure.persistence.postgres.repository;

import com.mudassir.eka.infrastructure.persistence.postgres.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {

    Page<AuditLogEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<AuditLogEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<AuditLogEntity> findByTenantIdAndActionOrderByCreatedAtDesc(UUID tenantId, String action, Pageable pageable);
}
