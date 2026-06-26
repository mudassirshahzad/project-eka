package com.mudassir.eka.infrastructure.persistence.postgres.adapter;

import com.mudassir.eka.domain.conversation.ChatSession;
import com.mudassir.eka.domain.conversation.ChatSessionId;
import com.mudassir.eka.domain.conversation.ChatSessionRepository;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.ChatSessionEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.ConversationEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.UserEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.mapper.ChatSessionPersistenceMapper;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.ChatSessionJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.ConversationJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.TenantJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ChatSessionRepositoryAdapter implements ChatSessionRepository {

    private final ChatSessionJpaRepository     chatSessionJpaRepository;
    private final ConversationJpaRepository    conversationJpaRepository;
    private final UserJpaRepository            userJpaRepository;
    private final TenantJpaRepository          tenantJpaRepository;
    private final ChatSessionPersistenceMapper mapper;

    @Override
    @Transactional
    public ChatSession save(ChatSession domain) {
        Optional<ChatSessionEntity> existing = chatSessionJpaRepository.findById(domain.getId().value());
        ChatSessionEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            mapper.updateEntity(entity, domain);
        } else {
            ConversationEntity conversation = conversationJpaRepository.getReferenceById(domain.getConversationId().value());
            UserEntity         user         = userJpaRepository.getReferenceById(domain.getUserId().value());
            TenantEntity       tenant       = tenantJpaRepository.getReferenceById(domain.getTenantId().value());
            entity = mapper.toEntity(domain, conversation, user, tenant);
        }
        return mapper.toDomain(chatSessionJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChatSession> findById(ChatSessionId id) {
        return chatSessionJpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChatSession> findActiveByConversationId(ConversationId conversationId) {
        ConversationEntity conversation = conversationJpaRepository.getReferenceById(conversationId.value());
        return chatSessionJpaRepository.findByConversationAndStatus(conversation, "ACTIVE")
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSession> findByConversationId(ConversationId conversationId) {
        ConversationEntity conversation = conversationJpaRepository.getReferenceById(conversationId.value());
        return chatSessionJpaRepository.findByConversationOrderByStartedAtAsc(conversation)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
