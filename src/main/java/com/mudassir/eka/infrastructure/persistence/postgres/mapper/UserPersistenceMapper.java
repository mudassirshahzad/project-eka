package com.mudassir.eka.infrastructure.persistence.postgres.mapper;

import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.User;
import com.mudassir.eka.domain.user.UserId;
import com.mudassir.eka.domain.user.UserRole;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.RoleEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserPersistenceMapper {

    public User toDomain(UserEntity e) {
        Set<UserRole> roles = e.getRoles().stream()
                .map(r -> UserRole.valueOf(r.getName()))
                .collect(Collectors.toSet());

        return User.reconstitute(
                UserId.of(e.getId()),
                TenantId.of(e.getTenant().getId()),
                e.getEmail(),
                e.getPasswordHash(),
                e.isActive(),
                roles,
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    public UserEntity toEntity(User d, TenantEntity tenant, Set<RoleEntity> roleEntities) {
        UserEntity entity = UserEntity.builder()
                .tenant(tenant)
                .email(d.getEmail())
                .passwordHash(d.getPasswordHash())
                .active(d.isActive())
                .roles(roleEntities)
                .build();
        entity.setId(d.getId().value());
        return entity;
    }
}
