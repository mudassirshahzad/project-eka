package com.mudassir.eka.application.user;

import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.User;
import com.mudassir.eka.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeactivateUserUseCase {

    private final UserApplicationService userService;

    public User execute(UserId id, TenantId tenantId) {
        Objects.requireNonNull(id, "userId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        log.debug("Deactivating user: id={} tenant={}", id, tenantId);
        return userService.deactivateUser(id, tenantId);
    }
}
