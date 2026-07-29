package com.mudassirshahzad.eka.application.user;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserRole;

import java.util.Set;

public record RegisterUserCommand(
        TenantId      tenantId,
        String        email,
        String        passwordHash,
        Set<UserRole> roles
) {}
