package com.mudassir.eka.infrastructure.persistence.postgres.adapter;

import com.mudassir.eka.domain.conversation.Conversation;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.conversation.ConversationRepository;
import com.mudassir.eka.domain.conversation.Message;
import com.mudassir.eka.domain.shared.PageRequest;
import com.mudassir.eka.domain.shared.PageResult;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.ConversationEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.MessageEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.entity.UserEntity;
import com.mudassir.eka.infrastructure.persistence.postgres.mapper.ConversationPersistenceMapper;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.ConversationJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.MessageJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.TenantJpaRepository;
import com.mudassir.eka.infrastructure.persistence.postgres.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConversationRepositoryAdapter implements ConversationRepository {

    private final ConversationJpaRepository conversationJpaRepository;
    private final MessageJpaRepository      messageJpaRepository;
    private final UserJpaRepository         userJpaRepository;
    private final TenantJpaRepository       tenantJpaRepository;
    private final ConversationPersistenceMapper mapper;

    @Override
    @Transactional
    public Conversation save(Conversation domain) {
        Optional<ConversationEntity> existing = conversationJpaRepository.findById(domain.getId().value());

        ConversationEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setTitle(domain.getTitle());
            entity.setDeletedAt(domain.getDeletedAt());

            persistNewMessages(domain, entity);
        } else {
            UserEntity   user   = userJpaRepository.getReferenceById(domain.getUserId().value());
            TenantEntity tenant = tenantJpaRepository.getReferenceById(domain.getTenantId().value());
            entity = mapper.toEntity(domain, user, tenant);

            domain.getMessages().forEach(m -> {
                MessageEntity me = mapper.messageToEntity(m, entity);
                entity.getMessages().add(me);
            });
        }

        return mapper.toDomain(conversationJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findById(ConversationId id) {
        return conversationJpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findByIdAndUserId(ConversationId id, UserId userId) {
        UserEntity user = userJpaRepository.getReferenceById(userId.value());
        return conversationJpaRepository.findByIdAndUser(id.value(), user).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Conversation> findByUserIdAndTenantId(UserId userId, TenantId tenantId,
                                                              PageRequest pageRequest) {
        UserEntity   user   = userJpaRepository.getReferenceById(userId.value());
        TenantEntity tenant = tenantJpaRepository.getReferenceById(tenantId.value());
        Page<ConversationEntity> page = conversationJpaRepository.findByUserAndTenant(
                user, tenant,
                org.springframework.data.domain.PageRequest.of(pageRequest.pageNumber(), pageRequest.pageSize())
        );
        return PageResult.of(
                page.getContent().stream().map(mapper::toDomain).toList(),
                pageRequest.pageNumber(),
                pageRequest.pageSize(),
                page.getTotalElements()
        );
    }

    @Override
    @Transactional
    public void softDelete(ConversationId id) {
        conversationJpaRepository.softDeleteById(id.value(), Instant.now());
    }

    private void persistNewMessages(Conversation domain, ConversationEntity entity) {
        int persisted = entity.getMessages().size();
        List<Message> domainMessages = domain.getMessages();
        for (int i = persisted; i < domainMessages.size(); i++) {
            MessageEntity me = mapper.messageToEntity(domainMessages.get(i), entity);
            entity.getMessages().add(me);
        }
    }
}
