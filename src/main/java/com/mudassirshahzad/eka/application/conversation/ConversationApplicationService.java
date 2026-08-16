package com.mudassirshahzad.eka.application.conversation;

import com.mudassirshahzad.eka.application.event.ConversationCreatedEvent;
import com.mudassirshahzad.eka.application.event.ConversationDeletedEvent;
import com.mudassirshahzad.eka.application.event.MessageAddedEvent;
import com.mudassirshahzad.eka.application.shared.DomainEventPublisher;
import com.mudassirshahzad.eka.application.shared.ResourceNotFoundException;
import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.conversation.ConversationRepository;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.conversation.MessageRole;
import com.mudassirshahzad.eka.domain.shared.PageRequest;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final MeterRegistry          meterRegistry;

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
    public Conversation getConversation(ConversationId id, UserId userId, TenantId tenantId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", id.value().toString()));
        return requireTenantMatch(conversation, tenantId);
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
        requireTenantMatch(conversation, cmd.tenantId());
        Message message = Message.userMessage(cmd.content());
        conversation.addMessage(message);
        Conversation saved = conversationRepository.save(conversation);
        eventPublisher.publish(new MessageAddedEvent(
                saved.getId(), message.id(), cmd.userId(), MessageRole.USER));
        return saved;
    }

    /**
     * Persists a generated assistant reply. Deliberately symmetrical with
     * {@link #addUserMessage(AddUserMessageCommand)} — same ownership check via
     * {@code findByIdAndUserId}, same tenant check, same aggregate mutation, same
     * {@link MessageAddedEvent} publication, differing only in {@link MessageRole} and citation
     * payload (ADR O02).
     */
    public Conversation addAssistantMessage(AddAssistantMessageCommand cmd) {
        Conversation conversation = conversationRepository.findByIdAndUserId(
                        cmd.conversationId(), cmd.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation", cmd.conversationId().value().toString()));
        requireTenantMatch(conversation, cmd.tenantId());
        Message message = Message.assistantMessage(cmd.content(), cmd.citations(), null);
        conversation.addMessage(message);
        Conversation saved = conversationRepository.save(conversation);
        eventPublisher.publish(new MessageAddedEvent(
                saved.getId(), message.id(), cmd.userId(), MessageRole.ASSISTANT));
        return saved;
    }

    /**
     * Defensive, explicit tenant check applied after every ownership-scoped fetch (ADR TN01) —
     * mirrors the precedent {@code PersistentConversationHistoryAdapter} set in P04.13 (ADR R01):
     * a mismatch is treated identically to "not found," never as a distinct "forbidden" outcome,
     * so a caller can never learn that a conversation exists in another tenant.
     *
     * <p>A mismatch increments {@code eka.authz.failures{reason=ownership}} (P05.4, ADR OB02) and
     * logs at WARN — operator-facing signals only, since the HTTP response stays indistinguishable
     * from a genuine 404. The plain "no such id/user" case from {@code findByIdAndUserId} itself is
     * deliberately not instrumented here: telling "doesn't exist" apart from "exists, wrong owner"
     * would need a second repository query, the exact duplication ADR AZ01 already rejected.
     */
    private Conversation requireTenantMatch(Conversation conversation, TenantId tenantId) {
        if (!conversation.getTenantId().equals(tenantId)) {
            log.warn("Tenant mismatch on conversation access: conversation={} requestedTenant={} actualTenant={}",
                    conversation.getId(), tenantId, conversation.getTenantId());
            meterRegistry.counter("eka.authz.failures", "reason", "ownership").increment();
            throw new ResourceNotFoundException("Conversation", conversation.getId().value().toString());
        }
        return conversation;
    }

    public Conversation renameConversation(RenameConversationCommand cmd) {
        Conversation conversation = conversationRepository.findByIdAndUserId(
                        cmd.conversationId(), cmd.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation", cmd.conversationId().value().toString()));
        requireTenantMatch(conversation, cmd.tenantId());
        conversation.rename(cmd.newTitle());
        return conversationRepository.save(conversation);
    }

    public void deleteConversation(ConversationId id, UserId userId, TenantId tenantId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", id.value().toString()));
        requireTenantMatch(conversation, tenantId);
        conversationRepository.softDelete(id);
        log.info("Conversation deleted: id={} user={}", id, userId);
        eventPublisher.publish(new ConversationDeletedEvent(id, userId));
    }
}
