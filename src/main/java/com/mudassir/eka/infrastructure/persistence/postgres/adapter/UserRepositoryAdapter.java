package com.mudassir.eka.infrastructure.persistence.postgres.adapter;

import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.User;
import com.mudassir.eka.domain.user.UserId;
import com.mudassir.eka.domain.user.UserRepository;
import com.mudassir.eka.domain.user.UserRole;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.RoleEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.UserEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.mapper.UserPersistenceMapper;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.RoleJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.TenantJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository   userJpaRepository;
    private final TenantJpaRepository tenantJpaRepository;
    private final RoleJpaRepository   roleJpaRepository;
    private final UserPersistenceMapper mapper;

    @Override
    @Transactional
    public User save(User domain) {
        Optional<UserEntity> existing = userJpaRepository.findById(domain.getId().value());

        UserEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setPasswordHash(domain.getPasswordHash());
            entity.setActive(domain.isActive());
            entity.setRoles(resolveRoles(domain.getRoles()));
        } else {
            TenantEntity tenant = tenantJpaRepository.getReferenceById(domain.getTenantId().value());
            Set<RoleEntity> roles = resolveRoles(domain.getRoles());
            entity = mapper.toEntity(domain, tenant, roles);
        }

        return mapper.toDomain(userJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        return userJpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmailAndTenantId(String email, TenantId tenantId) {
        TenantEntity tenant = tenantJpaRepository.getReferenceById(tenantId.value());
        return userJpaRepository.findByEmailAndTenant(email, tenant).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmailAndTenantId(String email, TenantId tenantId) {
        TenantEntity tenant = tenantJpaRepository.getReferenceById(tenantId.value());
        return userJpaRepository.existsByEmailAndTenant(email, tenant);
    }

    private Set<RoleEntity> resolveRoles(Set<UserRole> roles) {
        return roles.stream()
                .map(r -> roleJpaRepository.findByName(r.name())
                        .orElseThrow(() -> new IllegalStateException("Role not found: " + r.name())))
                .collect(Collectors.toSet());
    }
}
