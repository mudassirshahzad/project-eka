package com.mudassirshahzad.eka.application.user;

import com.mudassirshahzad.eka.domain.shared.TenantId;

public record AuthenticateUserCommand(String email, String rawPassword, TenantId tenantId) {}
