package com.mudassir.eka.application.chat;

import com.mudassir.eka.application.event.ChatSessionCompletedEvent;
import com.mudassir.eka.application.event.ChatSessionStartedEvent;
import com.mudassir.eka.application.event.ChatSessionTimedOutEvent;
import com.mudassir.eka.application.shared.DomainEventPublisher;
import com.mudassir.eka.application.shared.DuplicateResourceException;
import com.mudassir.eka.application.shared.ResourceNotFoundException;
import com.mudassir.eka.domain.conversation.ChatSession;
import com.mudassir.eka.domain.conversation.ChatSessionId;
import com.mudassir.eka.domain.conversation.ChatSessionRepository;
import com.mudassir.eka.domain.conversation.ConversationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatSessionApplicationService {

    private final ChatSessionRepository chatSessionRepository;
    private final DomainEventPublisher  eventPublisher;

    public ChatSession startSession(StartChatSessionCommand cmd) {
        chatSessionRepository.findActiveByConversationId(cmd.conversationId()).ifPresent(existing -> {
            throw new DuplicateResourceException(
                    "An active session already exists for conversation: " + cmd.conversationId());
        });
        ChatSession session = ChatSession.start(
                cmd.conversationId(), cmd.userId(), cmd.tenantId(), cmd.modelId());
        ChatSession saved = chatSessionRepository.save(session);
        log.info("Chat session started: id={} conversation={} model={}",
                saved.getId(), saved.getConversationId(), saved.getModelId());
        eventPublisher.publish(new ChatSessionStartedEvent(
                saved.getId(), saved.getConversationId(), saved.getUserId(),
                saved.getTenantId(), saved.getModelId()));
        return saved;
    }

    public ChatSession recordTurn(RecordTurnCommand cmd) {
        ChatSession session = loadSession(cmd.sessionId());
        session.recordTurn(cmd.promptTokens(), cmd.completionTokens(), cmd.latencyMs());
        return chatSessionRepository.save(session);
    }

    public ChatSession completeSession(ChatSessionId id) {
        ChatSession session = loadSession(id);
        session.complete();
        ChatSession saved = chatSessionRepository.save(session);
        log.info("Chat session completed: id={} conversation={} totalTokens={}",
                saved.getId(), saved.getConversationId(), saved.totalTokens());
        eventPublisher.publish(new ChatSessionCompletedEvent(
                saved.getId(), saved.getConversationId(), saved.totalTokens()));
        return saved;
    }

    public ChatSession timeoutSession(ChatSessionId id) {
        ChatSession session = loadSession(id);
        session.timeout();
        ChatSession saved = chatSessionRepository.save(session);
        log.info("Chat session timed out: id={} conversation={}", saved.getId(), saved.getConversationId());
        eventPublisher.publish(new ChatSessionTimedOutEvent(saved.getId(), saved.getConversationId()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<ChatSession> findActiveSession(ConversationId conversationId) {
        return chatSessionRepository.findActiveByConversationId(conversationId);
    }

    @Transactional(readOnly = true)
    public ChatSession getSession(ChatSessionId id) {
        return loadSession(id);
    }

    @Transactional(readOnly = true)
    public List<ChatSession> listSessionsByConversation(ConversationId conversationId) {
        return chatSessionRepository.findByConversationId(conversationId);
    }

    private ChatSession loadSession(ChatSessionId id) {
        return chatSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ChatSession", id.value().toString()));
    }
}
