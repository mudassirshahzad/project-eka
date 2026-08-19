package com.mudassirshahzad.eka.application.user;

import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.User;
import com.mudassirshahzad.eka.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserUseCase {

    private final UserApplicationService userService;

    /** {@code tenantId} added P06.1 (ADR PC02) — see {@link UserApplicationService#getUser}. */
    public User execute(UserId id, TenantId tenantId) {
        Objects.requireNonNull(id, "userId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return userService.getUser(id, tenantId);
    }
}
