package com.mudassirshahzad.eka.domain.query;

import com.mudassirshahzad.eka.domain.shared.PageRequest;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;

import java.util.Optional;

public interface QueryRepository {

    KnowledgeQuery save(KnowledgeQuery query);

    Optional<KnowledgeQuery> findById(QueryId id);

    PageResult<KnowledgeQuery> findByUserIdAndTenantId(UserId userId, TenantId tenantId, PageRequest pageRequest);
}
