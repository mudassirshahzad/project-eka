package com.mudassir.eka.infrastructure.persistence.postgres.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
    name = "chat_sessions",
    indexes = {
        @Index(name = "idx_chat_sessions_conversation", columnList = "conversation_id"),
        @Index(name = "idx_chat_sessions_user_tenant",  columnList = "tenant_id, user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionEntity extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private ConversationEntity conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private TenantEntity tenant;

    @Column(name = "model_id", nullable = false, updatable = false, length = 100)
    private String modelId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "total_prompt_tokens", nullable = false)
    @Builder.Default
    private int totalPromptTokens = 0;

    @Column(name = "total_completion_tokens", nullable = false)
    @Builder.Default
    private int totalCompletionTokens = 0;

    @Column(name = "total_latency_ms", nullable = false)
    @Builder.Default
    private long totalLatencyMs = 0L;

    @Column(name = "message_count", nullable = false)
    @Builder.Default
    private int messageCount = 0;

    // startedAt mirrors createdAt — captured separately for explicit domain semantics
    @Column(name = "started_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private Instant startedAt;

    @Column(name = "ended_at", columnDefinition = "TIMESTAMPTZ")
    private Instant endedAt;
}
