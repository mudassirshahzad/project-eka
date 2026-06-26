package com.mudassir.eka.domain.conversation;

import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Tracks one bounded interaction window within a {@link Conversation}.
 *
 * A Conversation is the persistent user-facing thread; a ChatSession is the
 * time-bounded LLM interaction within it. One conversation can have many sessions
 * (e.g. a user returns the next day). Sessions carry the LLM model identity and
 * cumulative token/latency metrics for cost attribution and observability.
 *
 * Invariants:
 * - Only ACTIVE sessions accept new turns via {@link #recordTurn}.
 * - {@link #endedAt} is set exactly once, when status leaves ACTIVE.
 */
public class ChatSession {

    private final ChatSessionId    id;
    private final ConversationId   conversationId;
    private final UserId           userId;
    private final TenantId         tenantId;
    private final String           modelId;
    private ChatSessionStatus      status;
    private int                    totalPromptTokens;
    private int                    totalCompletionTokens;
    private long                   totalLatencyMs;
    private int                    messageCount;
    private final Instant          startedAt;
    private Instant                endedAt;
    private Instant                updatedAt;

    // ── Factory ──────────────────────────────────────────────────────────────

    public static ChatSession start(
            ConversationId conversationId,
            UserId userId,
            TenantId tenantId,
            String modelId
    ) {
        ChatSession s = new ChatSession(
                ChatSessionId.generate(), conversationId, userId, tenantId,
                Objects.requireNonNull(modelId, "modelId"), Instant.now());
        s.status    = ChatSessionStatus.ACTIVE;
        s.updatedAt = s.startedAt;
        return s;
    }

    public static ChatSession reconstitute(
            ChatSessionId id,
            ConversationId conversationId,
            UserId userId,
            TenantId tenantId,
            String modelId,
            ChatSessionStatus status,
            int totalPromptTokens,
            int totalCompletionTokens,
            long totalLatencyMs,
            int messageCount,
            Instant startedAt,
            Instant endedAt,
            Instant updatedAt
    ) {
        ChatSession s = new ChatSession(id, conversationId, userId, tenantId, modelId, startedAt);
        s.status                = status;
        s.totalPromptTokens     = totalPromptTokens;
        s.totalCompletionTokens = totalCompletionTokens;
        s.totalLatencyMs        = totalLatencyMs;
        s.messageCount          = messageCount;
        s.endedAt               = endedAt;
        s.updatedAt             = updatedAt;
        return s;
    }

    private ChatSession(ChatSessionId id, ConversationId conversationId,
                        UserId userId, TenantId tenantId,
                        String modelId, Instant startedAt) {
        this.id             = Objects.requireNonNull(id,             "id");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.userId         = Objects.requireNonNull(userId,         "userId");
        this.tenantId       = Objects.requireNonNull(tenantId,       "tenantId");
        this.modelId        = modelId;
        this.startedAt      = Objects.requireNonNull(startedAt,      "startedAt");
    }

    // ── Behaviour ─────────────────────────────────────────────────────────────

    /**
     * Accumulates metrics for one user→assistant exchange.
     * Called by the application service after each successful LLM response.
     */
    public void recordTurn(int promptTokens, int completionTokens, long latencyMs) {
        requireActive();
        if (promptTokens    < 0) throw new IllegalArgumentException("promptTokens must be >= 0");
        if (completionTokens < 0) throw new IllegalArgumentException("completionTokens must be >= 0");
        if (latencyMs       < 0) throw new IllegalArgumentException("latencyMs must be >= 0");

        this.totalPromptTokens     += promptTokens;
        this.totalCompletionTokens += completionTokens;
        this.totalLatencyMs        += latencyMs;
        this.messageCount++;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        requireActive();
        this.status    = ChatSessionStatus.COMPLETED;
        this.endedAt   = Instant.now();
        this.updatedAt = this.endedAt;
    }

    public void timeout() {
        requireActive();
        this.status    = ChatSessionStatus.TIMED_OUT;
        this.endedAt   = Instant.now();
        this.updatedAt = this.endedAt;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean isActive() {
        return status == ChatSessionStatus.ACTIVE;
    }

    public int totalTokens() {
        return totalPromptTokens + totalCompletionTokens;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public ChatSessionId    getId()                  { return id; }
    public ConversationId   getConversationId()      { return conversationId; }
    public UserId           getUserId()              { return userId; }
    public TenantId         getTenantId()            { return tenantId; }
    public String           getModelId()             { return modelId; }
    public ChatSessionStatus getStatus()             { return status; }
    public int              getTotalPromptTokens()   { return totalPromptTokens; }
    public int              getTotalCompletionTokens(){ return totalCompletionTokens; }
    public long             getTotalLatencyMs()      { return totalLatencyMs; }
    public int              getMessageCount()        { return messageCount; }
    public Instant          getStartedAt()           { return startedAt; }
    public Instant          getEndedAt()             { return endedAt; }
    public Instant          getUpdatedAt()           { return updatedAt; }

    // ── Equality ──────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatSession s)) return false;
        return id.equals(s.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requireActive() {
        if (status != ChatSessionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "ChatSession %s is not ACTIVE (current: %s)".formatted(id, status));
        }
    }
}
