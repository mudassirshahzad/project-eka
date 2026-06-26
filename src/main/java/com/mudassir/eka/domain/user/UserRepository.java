package com.mudassir.eka.domain.user;

import com.mudassir.eka.domain.shared.TenantId;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByEmailAndTenantId(String email, TenantId tenantId);

    boolean existsByEmailAndTenantId(String email, TenantId tenantId);
}
