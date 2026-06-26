package com.mudassir.eka.infrastructure.persistence.postgres.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageEntity extends BaseUuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private ConversationEntity conversation;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "query_id")
    private UUID queryId;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<CitationEntity> citations = new ArrayList<>();

    // ── ChatSession linkage ───────────────────────────────────────────────────
    // Nullable: USER messages always belong to a session, but legacy rows do not.

    @Column(name = "session_id")
    private UUID sessionId;

    // ── LLM metadata — null for USER and SYSTEM role messages ────────────────

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "model_id", length = 100)
    private String modelId;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "finish_reason", length = 50)
    private String finishReason;
}
