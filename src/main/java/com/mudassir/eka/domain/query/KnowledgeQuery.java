package com.mudassir.eka.domain.query;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class KnowledgeQuery {

    private final QueryId        id;
    private final UserId         userId;
    private final TenantId       tenantId;
    private final ConversationId conversationId;
    private final String         originalText;
    private String               rewrittenText;
    private final MetadataFilter filter;
    private final List<ChunkId>  retrievedChunkIds;
    private Integer              retrievedCount;
    private Long                 latencyMs;
    private final Instant        createdAt;

    public static KnowledgeQuery create(
            UserId userId,
            TenantId tenantId,
            ConversationId conversationId,
            String originalText,
            MetadataFilter filter
    ) {
        KnowledgeQuery q = new KnowledgeQuery(QueryId.generate(), userId, tenantId, conversationId,
                Objects.requireNonNull(originalText), filter, Instant.now());
        return q;
    }

    public static KnowledgeQuery reconstitute(
            QueryId id, UserId userId, TenantId tenantId, ConversationId conversationId,
            String originalText, String rewrittenText, MetadataFilter filter,
            List<ChunkId> retrievedChunkIds, Integer retrievedCount, Long latencyMs, Instant createdAt
    ) {
        KnowledgeQuery q = new KnowledgeQuery(id, userId, tenantId, conversationId, originalText, filter, createdAt);
        q.rewrittenText       = rewrittenText;
        q.retrievedChunkIds.addAll(retrievedChunkIds);
        q.retrievedCount      = retrievedCount;
        q.latencyMs           = latencyMs;
        return q;
    }

    private KnowledgeQuery(QueryId id, UserId userId, TenantId tenantId, ConversationId conversationId,
                           String originalText, MetadataFilter filter, Instant createdAt) {
        this.id              = Objects.requireNonNull(id);
        this.userId          = Objects.requireNonNull(userId);
        this.tenantId        = Objects.requireNonNull(tenantId);
        this.conversationId  = conversationId;
        this.originalText    = originalText;
        this.filter          = filter != null ? filter : MetadataFilter.NONE;
        this.createdAt       = createdAt;
        this.retrievedChunkIds = new ArrayList<>();
    }

    public void setRewrittenText(String rewrittenText)   { this.rewrittenText = rewrittenText; }

    public void recordRetrieval(List<ChunkId> chunkIds, long latencyMs) {
        this.retrievedChunkIds.clear();
        this.retrievedChunkIds.addAll(chunkIds);
        this.retrievedCount = chunkIds.size();
        this.latencyMs      = latencyMs;
    }

    public String effectiveQueryText() {
        return rewrittenText != null && !rewrittenText.isBlank() ? rewrittenText : originalText;
    }

    public QueryId        getId()                { return id; }
    public UserId         getUserId()            { return userId; }
    public TenantId       getTenantId()          { return tenantId; }
    public ConversationId getConversationId()    { return conversationId; }
    public String         getOriginalText()      { return originalText; }
    public String         getRewrittenText()     { return rewrittenText; }
    public MetadataFilter getFilter()            { return filter; }
    public List<ChunkId>  getRetrievedChunkIds() { return Collections.unmodifiableList(retrievedChunkIds); }
    public Integer        getRetrievedCount()    { return retrievedCount; }
    public Long           getLatencyMs()         { return latencyMs; }
    public Instant        getCreatedAt()         { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KnowledgeQuery q)) return false;
        return id.equals(q.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
