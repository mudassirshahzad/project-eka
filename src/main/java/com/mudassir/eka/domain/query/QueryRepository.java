package com.mudassir.eka.domain.query;

import com.mudassir.eka.domain.shared.PageRequest;
import com.mudassir.eka.domain.shared.PageResult;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

import java.util.Optional;

public interface QueryRepository {

    KnowledgeQuery save(KnowledgeQuery query);

    Optional<KnowledgeQuery> findById(QueryId id);

    PageResult<KnowledgeQuery> findByUserIdAndTenantId(UserId userId, TenantId tenantId, PageRequest pageRequest);
}
