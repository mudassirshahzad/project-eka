package com.mudassirshahzad.eka.application.query;

import com.mudassirshahzad.eka.application.event.QuerySubmittedEvent;
import com.mudassirshahzad.eka.application.shared.DomainEventPublisher;
import com.mudassirshahzad.eka.application.shared.ResourceNotFoundException;
import com.mudassirshahzad.eka.domain.query.KnowledgeQuery;
import com.mudassirshahzad.eka.domain.query.QueryId;
import com.mudassirshahzad.eka.domain.query.QueryRepository;
import com.mudassirshahzad.eka.domain.shared.PageRequest;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
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
