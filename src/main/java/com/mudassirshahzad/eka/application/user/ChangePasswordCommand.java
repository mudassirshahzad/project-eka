package com.mudassirshahzad.eka.application.user;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

public record ChangePasswordCommand(
        UserId   userId,
        TenantId tenantId,
        String   newPasswordHash
) {}
