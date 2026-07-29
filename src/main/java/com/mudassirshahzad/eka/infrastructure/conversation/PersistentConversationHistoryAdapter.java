package com.mudassirshahzad.eka.infrastructure.conversation;

import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.conversation.ConversationRepository;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.generation.port.ConversationHistoryPort;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * {@link ConversationHistoryPort} implementation backed by the same {@link ConversationRepository}
 * that {@code ConversationApplicationService} writes to (P04.13.2 reconciliation).
 *
 * <p>Prior to this milestone, conversation memory was served by an in-memory adapter
 * (ADR M04's dev-grade seam) that was never populated by the real conversation write path —
 * {@code ConversationApplicationService.addUserMessage()} persists to {@link ConversationRepository}
 * only, so generation-time memory was always empty in practice. This adapter closes that gap by
 * reading directly from the persisted {@link Conversation} aggregate, which already exposes the
 * exact windowing logic this port needs via {@link Conversation#recentMessages(int)}.
 *
 * <p><b>Tenant isolation:</b> {@link ConversationRepository#findById(ConversationId)} is not
 * tenant-scoped at the query level (it is used by {@code getConversation}/{@code addUserMessage}
 * call sites that separately verify ownership via {@code findByIdAndUserId}). This adapter is a
 * new call site with only a {@link TenantId}, not a {@code UserId}, so it independently verifies
 * {@code conversation.getTenantId().equals(tenantId)} after fetch and treats a mismatch identically
 * to "not found" — never leaking another tenant's conversation history.
 *
 * <p>No new abstraction is introduced: this adapter composes two pieces of existing architecture
 * ({@link ConversationRepository} and {@link Conversation#recentMessages(int)}) behind the
 * unchanged {@link ConversationHistoryPort} contract. {@code ConversationHistoryPort} and
 * {@code GenerationService} are untouched.
 *
 * <p>Logging: conversation IDs and message counts are logged at DEBUG. Message content is never
 * logged.
 */
@Slf4j
@Component
public class PersistentConversationHistoryAdapter implements ConversationHistoryPort {

    private final ConversationRepository conversationRepository;

    public PersistentConversationHistoryAdapter(ConversationRepository conversationRepository) {
        this.conversationRepository = Objects.requireNonNull(
                conversationRepository, "conversationRepository must not be null");
    }

    @Override
    public List<Message> getRecentMessages(ConversationId conversationId, TenantId tenantId, int maxMessages) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(tenantId,       "tenantId must not be null");
        if (maxMessages < 0) {
            throw new IllegalArgumentException("maxMessages must be >= 0 but was " + maxMessages);
        }

        if (maxMessages == 0) {
            return List.of();
        }

        return conversationRepository.findById(conversationId)
                .filter(conversation -> belongsToTenant(conversation, conversationId, tenantId))
                .map(conversation -> logAndWindow(conversation, conversationId, maxMessages))
                .orElseGet(() -> {
                    log.debug("Conversation history requested for unknown conversation: conversationId={}",
                            conversationId);
                    return List.of();
                });
    }

    private boolean belongsToTenant(Conversation conversation, ConversationId conversationId, TenantId tenantId) {
        boolean matches = conversation.getTenantId().equals(tenantId);
        if (!matches) {
            log.warn("Conversation history requested with mismatched tenant: conversationId={}", conversationId);
        }
        return matches;
    }

    private List<Message> logAndWindow(Conversation conversation, ConversationId conversationId, int maxMessages) {
        List<Message> window = conversation.recentMessages(maxMessages);
        log.debug("Conversation history fetched: conversationId={} requested={} returned={}",
                conversationId, maxMessages, window.size());
        return window;
    }
}
