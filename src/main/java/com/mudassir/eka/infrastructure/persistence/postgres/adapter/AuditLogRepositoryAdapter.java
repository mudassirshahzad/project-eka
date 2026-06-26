package com.mudassir.eka.infrastructure.persistence.postgres.adapter;

import com.mudassir.eka.domain.shared.AuditLog;
import com.mudassir.eka.domain.shared.AuditLogRepository;
import com.mudassir.eka.domain.shared.PageRequest;
import com.mudassir.eka.domain.shared.PageResult;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.AuditLogEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.AuditLogJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;

@Component
@RequiredArgsConstructor
public class AuditLogRepositoryAdapter implements AuditLogRepository {

    private final AuditLogJpaRepository auditLogJpaRepository;

    @Override
    @Transactional
    public void save(AuditLog auditLog) {
        AuditLogEntity entity = AuditLogEntity.builder()
                .tenantId(auditLog.tenantId().value())
                .userId(auditLog.userId() != null ? auditLog.userId().value() : null)
                .action(auditLog.action())
                .resourceType(auditLog.resourceType())
                .resourceId(auditLog.resourceId())
                .details(auditLog.details().isEmpty() ? null : auditLog.details())
                .ipAddress(auditLog.ipAddress())
                .build();
        auditLogJpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuditLog> findByTenantId(TenantId tenantId, PageRequest pageRequest) {
        Page<AuditLogEntity> page = auditLogJpaRepository.findByTenantIdOrderByCreatedAtDesc(
                tenantId.value(),
                org.springframework.data.domain.PageRequest.of(pageRequest.pageNumber(), pageRequest.pageSize())
        );
        return PageResult.of(
                page.getContent().stream().map(this::toDomain).toList(),
                pageRequest.pageNumber(),
                pageRequest.pageSize(),
                page.getTotalElements()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuditLog> findByUserId(UserId userId, PageRequest pageRequest) {
        Page<AuditLogEntity> page = auditLogJpaRepository.findByUserIdOrderByCreatedAtDesc(
                userId.value(),
                org.springframework.data.domain.PageRequest.of(pageRequest.pageNumber(), pageRequest.pageSize())
        );
        return PageResult.of(
                page.getContent().stream().map(this::toDomain).toList(),
                pageRequest.pageNumber(),
                pageRequest.pageSize(),
                page.getTotalElements()
        );
    }

    private AuditLog toDomain(AuditLogEntity e) {
        return new AuditLog(
                TenantId.of(e.getTenantId()),
                e.getUserId() != null ? UserId.of(e.getUserId()) : null,
                e.getAction(),
                e.getResourceType(),
                e.getResourceId(),
                e.getDetails() != null ? e.getDetails() : java.util.Map.of(),
                e.getIpAddress(),
                e.getCreatedAt()
        );
    }
}
