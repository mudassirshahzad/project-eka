package com.mudassir.eka.application.query;

import com.mudassir.eka.domain.query.KnowledgeQuery;
import com.mudassir.eka.domain.shared.PageRequest;
import com.mudassir.eka.domain.shared.PageResult;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListKnowledgeQueriesUseCase {

    private final QueryApplicationService queryService;

    public PageResult<KnowledgeQuery> execute(UserId userId, TenantId tenantId,
                                              PageRequest pageRequest) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return queryService.listQueriesByUser(userId, tenantId, pageRequest);
    }
}
