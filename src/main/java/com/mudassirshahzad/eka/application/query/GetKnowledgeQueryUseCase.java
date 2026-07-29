package com.mudassirshahzad.eka.application.query;

import com.mudassirshahzad.eka.domain.query.KnowledgeQuery;
import com.mudassirshahzad.eka.domain.query.QueryId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetKnowledgeQueryUseCase {

    private final QueryApplicationService queryService;

    public KnowledgeQuery execute(QueryId id) {
        Objects.requireNonNull(id, "queryId must not be null");
        return queryService.getQuery(id);
    }
}
