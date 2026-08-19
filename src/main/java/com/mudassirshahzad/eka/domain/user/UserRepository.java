package com.mudassirshahzad.eka.domain.user;

import com.mudassirshahzad.eka.domain.shared.TenantId;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByEmailAndTenantId(String email, TenantId tenantId);

    boolean existsByEmailAndTenantId(String email, TenantId tenantId);

    /** Added P06.1 (ADR PC03) — powers the bootstrap endpoint's "is this tenant already
     *  initialized" check; a tenant with zero users is eligible for first-user bootstrap. */
    boolean existsByTenantId(TenantId tenantId);
}
