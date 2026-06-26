package com.mudassir.eka.domain.shared;

import com.mudassir.eka.domain.user.UserId;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;

public record AuditLog(
        TenantId tenantId,
        UserId userId,
        String action,
        String resourceType,
        String resourceId,
        Map<String, Object> details,
        InetAddress ipAddress,
        Instant createdAt
) {

    public static AuditLog of(
            TenantId tenantId,
            UserId userId,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> details,
            InetAddress ipAddress
    ) {
        return new AuditLog(tenantId, userId, action, resourceType, resourceId, details, ipAddress, Instant.now());
    }

    public static AuditLog system(
            TenantId tenantId,
            String action,
            String resourceType,
            String resourceId
    ) {
        return new AuditLog(tenantId, null, action, resourceType, resourceId, Map.of(), null, Instant.now());
    }
}
