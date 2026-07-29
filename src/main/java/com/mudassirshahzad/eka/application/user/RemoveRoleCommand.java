package com.mudassirshahzad.eka.application.user;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;

public record RemoveRoleCommand(
        UserId   userId,
        TenantId tenantId,
        UserRole role
) {}
