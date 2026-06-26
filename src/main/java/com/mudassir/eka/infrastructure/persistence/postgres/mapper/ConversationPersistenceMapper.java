package com.mudassir.eka.infrastructure.persistence.postgres.mapper;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.conversation.Citation;
import com.mudassir.eka.domain.conversation.Conversation;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.conversation.Message;
import com.mudassir.eka.domain.conversation.MessageRole;
import com.mudassir.eka.domain.query.QueryId;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.CitationEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.ConversationEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.MessageEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ConversationPersistenceMapper {

    public Conversation toDomain(ConversationEntity e) {
        List<Message> messages = e.getMessages().stream()
                .map(this::messageToDomain)
                .toList();

        return Conversation.reconstitute(
                ConversationId.of(e.getId()),
                UserId.of(e.getUser().getId()),
                TenantId.of(e.getTenant().getId()),
                e.getTitle(),
                messages,
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getDeletedAt()
        );
    }

    public Message messageToDomain(MessageEntity e) {
        List<Citation> citations = e.getCitations().stream()
                .map(c -> Citation.of(ChunkId.of(c.getChunkId()), c.getRelevanceScore()))
                .toList();

        return new Message(
                e.getId(),
                MessageRole.valueOf(e.getRole()),
                e.getContent(),
                citations,
                e.getQueryId() != null ? QueryId.of(e.getQueryId()) : null,
                e.getCreatedAt()
        );
    }

    public ConversationEntity toEntity(Conversation d, UserEntity user, TenantEntity tenant) {
        ConversationEntity entity = ConversationEntity.builder()
                .user(user)
                .tenant(tenant)
                .title(d.getTitle())
                .deletedAt(d.getDeletedAt())
                .build();
        entity.setId(d.getId().value());
        return entity;
    }

    public MessageEntity messageToEntity(Message m, ConversationEntity conversation) {
        return messageToEntity(m, conversation, null);
    }

    public MessageEntity messageToEntity(Message m, ConversationEntity conversation, UUID sessionId) {
        MessageEntity entity = MessageEntity.builder()
                .conversation(conversation)
                .role(m.role().name())
                .content(m.content())
                .queryId(m.queryId() != null ? m.queryId().value() : null)
                .sessionId(sessionId)
                .build();
        entity.setId(m.id());

        List<CitationEntity> citationEntities = m.citations().stream()
                .map(c -> citationToEntity(c, entity))
                .toList();
        entity.getCitations().addAll(citationEntities);

        return entity;
    }

    private CitationEntity citationToEntity(Citation c, MessageEntity message) {
        CitationEntity entity = CitationEntity.builder()
                .message(message)
                .chunkId(c.chunkId().value())
                .relevanceScore(c.relevanceScore())
                .build();
        entity.setId(java.util.UUID.randomUUID());
        return entity;
    }
}
