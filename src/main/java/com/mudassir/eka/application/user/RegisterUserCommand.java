package com.mudassir.eka.application.user;

import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserRole;

import java.util.Set;

public record RegisterUserCommand(
        TenantId      tenantId,
        String        email,
        String        passwordHash,
        Set<UserRole> roles
) {}
