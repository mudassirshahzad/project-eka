package com.mudassir.eka.infrastructure.persistence.postgres.adapter;

import com.mudassir.eka.domain.query.KnowledgeQuery;
import com.mudassir.eka.domain.query.QueryId;
import com.mudassir.eka.domain.query.QueryRepository;
import com.mudassir.eka.domain.shared.PageRequest;
import com.mudassir.eka.domain.shared.PageResult;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.QueryEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.UserEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.mapper.QueryPersistenceMapper;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.QueryJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.TenantJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class QueryRepositoryAdapter implements QueryRepository {

    private final QueryJpaRepository  queryJpaRepository;
    private final UserJpaRepository   userJpaRepository;
    private final TenantJpaRepository tenantJpaRepository;
    private final QueryPersistenceMapper mapper;

    @Override
    @Transactional
    public KnowledgeQuery save(KnowledgeQuery domain) {
        UserEntity   user   = userJpaRepository.getReferenceById(domain.getUserId().value());
        TenantEntity tenant = tenantJpaRepository.getReferenceById(domain.getTenantId().value());
        QueryEntity entity  = mapper.toEntity(domain, user, tenant);
        return mapper.toDomain(queryJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KnowledgeQuery> findById(QueryId id) {
        return queryJpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<KnowledgeQuery> findByUserIdAndTenantId(UserId userId, TenantId tenantId,
                                                               PageRequest pageRequest) {
        UserEntity   user   = userJpaRepository.getReferenceById(userId.value());
        TenantEntity tenant = tenantJpaRepository.getReferenceById(tenantId.value());
        Page<QueryEntity> page = queryJpaRepository.findByUserAndTenant(
                user, tenant,
                org.springframework.data.domain.PageRequest.of(pageRequest.pageNumber(), pageRequest.pageSize())
        );
        return PageResult.of(
                page.getContent().stream().map(mapper::toDomain).toList(),
                pageRequest.pageNumber(),
                pageRequest.pageSize(),
                page.getTotalElements()
        );
    }
}
