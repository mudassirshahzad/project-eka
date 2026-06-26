package com.mudassir.eka.application.user;

import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

public record ChangePasswordCommand(
        UserId   userId,
        TenantId tenantId,
        String   newPasswordHash
) {}
