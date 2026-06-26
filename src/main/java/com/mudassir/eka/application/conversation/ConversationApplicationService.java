package com.mudassir.eka.application.conversation;

import com.mudassir.eka.application.event.ConversationCreatedEvent;
import com.mudassir.eka.application.event.ConversationDeletedEvent;
import com.mudassir.eka.application.event.MessageAddedEvent;
import com.mudassir.eka.application.shared.DomainEventPublisher;
import com.mudassir.eka.application.shared.ResourceNotFoundException;
import com.mudassir.eka.domain.conversation.Conversation;
import com.mudassir.eka.domain.conversation.ConversationId;
import com.mudassir.eka.domain.conversation.ConversationRepository;
import com.mudassir.eka.domain.conversation.Message;
import com.mudassir.eka.domain.conversation.MessageRole;
import com.mudassir.eka.domain.shared.PageRequest;
import com.mudassir.eka.domain.shared.PageResult;
import com.mudassir.eka.domain.shared.TenantId;
import com.mudassir.eka.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConversationApplicationService {

    private final ConversationRepository conversationRepository;
    private final DomainEventPublisher   eventPublisher;

    public Conversation createConversation(CreateConversationCommand cmd) {
        Conversation conversation = Conversation.create(cmd.userId(), cmd.tenantId(), cmd.title());
        Conversation saved = conversationRepository.save(conversation);
        log.info("Conversation created: id={} user={} tenant={}",
                saved.getId(), saved.getUserId(), saved.getTenantId());
        eventPublisher.publish(new ConversationCreatedEvent(
                saved.getId(), saved.getUserId(), saved.getTenantId(), saved.getTitle()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Conversation getConversation(ConversationId id, UserId userId) {
        return conversationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", id.value().toString()));
    }

    @Transactional(readOnly = true)
    public PageResult<Conversation> listConversations(UserId userId, TenantId tenantId,
                                                       PageRequest pageRequest) {
        return conversationRepository.findByUserIdAndTenantId(userId, tenantId, pageRequest);
    }

    public Conversation addUserMessage(AddUserMessageCommand cmd) {
        Conversation conversation = conversationRepository.findByIdAndUserId(
                        cmd.conversationId(), cmd.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation", cmd.conversationId().value().toString()));
        Message message = Message.userMessage(cmd.content());
        conversation.addMessage(message);
        Conversation saved = conversationRepository.save(conversation);
        eventPublisher.publish(new MessageAddedEvent(
                saved.getId(), message.id(), cmd.userId(), MessageRole.USER));
        return saved;
    }

    public Conversation renameConversation(RenameConversationCommand cmd) {
        Conversation conversation = conversationRepository.findByIdAndUserId(
                        cmd.conversationId(), cmd.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation", cmd.conversationId().value().toString()));
        conversation.rename(cmd.newTitle());
        return conversationRepository.save(conversation);
    }

    public void deleteConversation(ConversationId id, UserId userId) {
        conversationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", id.value().toString()));
        conversationRepository.softDelete(id);
        log.info("Conversation deleted: id={} user={}", id, userId);
        eventPublisher.publish(new ConversationDeletedEvent(id, userId));
    }
}
