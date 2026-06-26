package com.mudassir.eka.infrastructure.persistence.postgres.mapper;

import com.mudassir.eka.domain.conversation.ChatSession;
import com.mudassir.eka.domain.conversation.ChatSessionId;
import com.mudassir.eka.domain.conversation.ChatSessionStatus;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.ChatSessionEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.ConversationEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class ChatSessionPersistenceMapper {

    public ChatSession toDomain(ChatSessionEntity e) {
        return ChatSession.reconstitute(
                ChatSessionId.of(e.getId()),
                ConversationId.of(e.getConversation().getId()),
                UserId.of(e.getUser().getId()),
                TenantId.of(e.getTenant().getId()),
                e.getModelId(),
                ChatSessionStatus.valueOf(e.getStatus()),
                e.getTotalPromptTokens(),
                e.getTotalCompletionTokens(),
                e.getTotalLatencyMs(),
                e.getMessageCount(),
                e.getStartedAt(),
                e.getEndedAt(),
                e.getUpdatedAt()
        );
    }

    public ChatSessionEntity toEntity(ChatSession d, ConversationEntity conversation,
                                       UserEntity user, TenantEntity tenant) {
        ChatSessionEntity entity = ChatSessionEntity.builder()
                .conversation(conversation)
                .user(user)
                .tenant(tenant)
                .modelId(d.getModelId())
                .status(d.getStatus().name())
                .totalPromptTokens(d.getTotalPromptTokens())
                .totalCompletionTokens(d.getTotalCompletionTokens())
                .totalLatencyMs(d.getTotalLatencyMs())
                .messageCount(d.getMessageCount())
                .startedAt(d.getStartedAt())
                .endedAt(d.getEndedAt())
                .build();
        entity.setId(d.getId().value());
        return entity;
    }

    public void updateEntity(ChatSessionEntity entity, ChatSession d) {
        entity.setStatus(d.getStatus().name());
        entity.setTotalPromptTokens(d.getTotalPromptTokens());
        entity.setTotalCompletionTokens(d.getTotalCompletionTokens());
        entity.setTotalLatencyMs(d.getTotalLatencyMs());
        entity.setMessageCount(d.getMessageCount());
        entity.setEndedAt(d.getEndedAt());
        entity.setUpdatedAt(d.getUpdatedAt());
    }
}
