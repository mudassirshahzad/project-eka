package com.mudassir.eka.application.query;

import com.mudassir.eka.application.event.QuerySubmittedEvent;
import com.mudassir.eka.application.shared.DomainEventPublisher;
import com.mudassir.eka.application.shared.ResourceNotFoundException;
import com.mudassir.eka.domain.query.KnowledgeQuery;
import com.mudassir.eka.domain.query.QueryId;
import com.mudassir.eka.domain.query.QueryRepository;
import com.mudassir.eka.domain.shared.PageRequest;
import com.mudassir.eka.domain.shared.PageResult;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class QueryApplicationService {

    private final QueryRepository      queryRepository;
    private final DomainEventPublisher eventPublisher;

    public KnowledgeQuery submitQuery(SubmitQueryCommand cmd) {
        KnowledgeQuery query = KnowledgeQuery.create(
                cmd.userId(), cmd.tenantId(), cmd.conversationId(),
                cmd.queryText(), cmd.filter());
        KnowledgeQuery saved = queryRepository.save(query);
        log.info("Query submitted: id={} user={} tenant={}",
                saved.getId(), saved.getUserId(), saved.getTenantId());
        eventPublisher.publish(new QuerySubmittedEvent(
                saved.getId(), saved.getUserId(), saved.getTenantId(),
                saved.getConversationId(), saved.getOriginalText()));
        return saved;
    }

    @Transactional(readOnly = true)
    public KnowledgeQuery getQuery(QueryId id) {
        return queryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Query", id.value().toString()));
    }

    @Transactional(readOnly = true)
    public PageResult<KnowledgeQuery> listQueriesByUser(UserId userId, TenantId tenantId,
                                                         PageRequest pageRequest) {
        return queryRepository.findByUserIdAndTenantId(userId, tenantId, pageRequest);
    }
}
