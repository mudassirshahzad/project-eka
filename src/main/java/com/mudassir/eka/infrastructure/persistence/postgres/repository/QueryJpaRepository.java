package com.mudassir.eka.infrastructure.persistence.postgres.repository;

import com.mudassir.eka.infrastructure.persistence.postgres.entity.QueryEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface QueryJpaRepository extends JpaRepository<QueryEntity, UUID> {

    Page<QueryEntity> findByUserAndTenant(UserEntity user, TenantEntity tenant, Pageable pageable);

    Page<QueryEntity> findByConversationId(UUID conversationId, Pageable pageable);
}
