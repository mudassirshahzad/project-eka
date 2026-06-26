package com.mudassir.eka.application.user;

import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import com.mudassir.eka.domain.user.UserRole;

public record RemoveRoleCommand(
        UserId   userId,
        TenantId tenantId,
        UserRole role
) {}
