package com.mudassirshahzad.eka.domain.shared;

import com.mudassirshahzad.eka.domain.user.UserId;

public interface AuditLogRepository {

    void save(AuditLog auditLog);

    PageResult<AuditLog> findByTenantId(TenantId tenantId, PageRequest pageRequest);

    PageResult<AuditLog> findByUserId(UserId userId, PageRequest pageRequest);
}
