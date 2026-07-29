package com.mudassirshahzad.eka.infrastructure.persistence.postgres.mapper;

import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.query.KnowledgeQuery;
import com.mudassirshahzad.eka.domain.query.MetadataFilter;
import com.mudassirshahzad.eka.domain.query.QueryId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.QueryEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QueryPersistenceMapper {

    public KnowledgeQuery toDomain(QueryEntity e) {
        MetadataFilter filter = e.getFilterJson() != null
                ? new MetadataFilter(e.getFilterJson())
                : MetadataFilter.NONE;

        return KnowledgeQuery.reconstitute(
                QueryId.of(e.getId()),
                UserId.of(e.getUser().getId()),
                TenantId.of(e.getTenant().getId()),
                e.getConversationId() != null ? ConversationId.of(e.getConversationId()) : null,
                e.getOriginalText(),
                e.getRewrittenText(),
                filter,
                List.of(),
                e.getRetrievedCount(),
                e.getLatencyMs(),
                e.getCreatedAt()
        );
    }

    public QueryEntity toEntity(KnowledgeQuery d, UserEntity user, TenantEntity tenant) {
        QueryEntity entity = QueryEntity.builder()
                .user(user)
                .tenant(tenant)
                .conversationId(d.getConversationId() != null ? d.getConversationId().value() : null)
                .originalText(d.getOriginalText())
                .rewrittenText(d.getRewrittenText())
                .filterJson(d.getFilter().criteria().isEmpty() ? null : d.getFilter().criteria())
                .retrievedCount(d.getRetrievedCount())
                .latencyMs(d.getLatencyMs())
                .build();
        entity.setId(d.getId().value());
        return entity;
    }
}
