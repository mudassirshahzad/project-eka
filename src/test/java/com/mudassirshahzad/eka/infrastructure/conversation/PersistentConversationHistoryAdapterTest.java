package com.mudassirshahzad.eka.infrastructure.conversation;

import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.conversation.ConversationRepository;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistentConversationHistoryAdapterTest {

    @Mock
    private ConversationRepository conversationRepository;

    private final TenantId tenantId = TenantId.generate();
    private final UserId   userId   = UserId.generate();

    private PersistentConversationHistoryAdapter adapter() {
        return new PersistentConversationHistoryAdapter(conversationRepository);
    }

    // ── Constructor / null guards ────────────────────────────────────────────

    @Test
    void constructor_throwsOnNullRepository() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PersistentConversationHistoryAdapter(null))
                .withMessageContaining("conversationRepository");
    }

    @Test
    void getRecentMessages_throwsOnNullConversationId() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter().getRecentMessages(null, tenantId, 5))
                .withMessageContaining("conversationId");
    }

    @Test
    void getRecentMessages_throwsOnNullTenantId() {
        ConversationId id = ConversationId.generate();
        assertThatNullPointerException()
                .isThrownBy(() -> adapter().getRecentMessages(id, null, 5))
                .withMessageContaining("tenantId");
    }

    @Test
    void getRecentMessages_throwsOnNegativeMaxMessages() {
        ConversationId id = ConversationId.generate();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> adapter().getRecentMessages(id, tenantId, -1))
                .withMessageContaining("maxMessages");
    }

    // ── maxMessages == 0 ──────────────────────────────────────────────────────

    @Test
    void getRecentMessages_zeroMaxMessages_returnsEmptyWithoutQueryingRepository() {
        ConversationId id = ConversationId.generate();

        List<Message> result = adapter().getRecentMessages(id, tenantId, 0);

        assertThat(result).isEmpty();
        verify(conversationRepository, never()).findById(any());
    }

    // ── Missing conversation ─────────────────────────────────────────────────

    @Test
    void getRecentMessages_unknownConversation_returnsEmptyList() {
        ConversationId id = ConversationId.generate();
        when(conversationRepository.findById(id)).thenReturn(Optional.empty());

        List<Message> result = adapter().getRecentMessages(id, tenantId, 10);

        assertThat(result).isEmpty();
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Test
    void getRecentMessages_conversationBelongsToDifferentTenant_returnsEmptyList() {
        ConversationId id = ConversationId.generate();
        Conversation foreignConversation = Conversation.create(userId, TenantId.generate(), "Someone else's chat");
        foreignConversation.addMessage(Message.userMessage("secret question"));
        when(conversationRepository.findById(id)).thenReturn(Optional.of(foreignConversation));

        List<Message> result = adapter().getRecentMessages(id, tenantId, 10);

        assertThat(result).isEmpty();
    }

    // ── Real conversation, same tenant ───────────────────────────────────────

    @Test
    void getRecentMessages_returnsMessagesFromPersistedConversation() {
        ConversationId id = ConversationId.generate();
        Conversation conversation = Conversation.create(userId, tenantId, "My chat");
        Message first  = Message.userMessage("What is RAG?");
        Message second = Message.assistantMessage("Retrieval augmented generation.", List.of(), null);
        conversation.addMessage(first);
        conversation.addMessage(second);
        when(conversationRepository.findById(id)).thenReturn(Optional.of(conversation));

        List<Message> result = adapter().getRecentMessages(id, tenantId, 10);

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void getRecentMessages_windowsToMostRecentMessages() {
        ConversationId id = ConversationId.generate();
        Conversation conversation = Conversation.create(userId, tenantId, "Long chat");
        Message m1 = Message.userMessage("first");
        Message m2 = Message.userMessage("second");
        Message m3 = Message.userMessage("third");
        conversation.addMessage(m1);
        conversation.addMessage(m2);
        conversation.addMessage(m3);
        when(conversationRepository.findById(id)).thenReturn(Optional.of(conversation));

        List<Message> result = adapter().getRecentMessages(id, tenantId, 2);

        assertThat(result).containsExactly(m2, m3);
    }

    @Test
    void getRecentMessages_fewerMessagesThanWindow_returnsAllOfThem() {
        ConversationId id = ConversationId.generate();
        Conversation conversation = Conversation.create(userId, tenantId, "Short chat");
        Message only = Message.userMessage("only message");
        conversation.addMessage(only);
        when(conversationRepository.findById(id)).thenReturn(Optional.of(conversation));

        List<Message> result = adapter().getRecentMessages(id, tenantId, 50);

        assertThat(result).containsExactly(only);
    }

    @Test
    void getRecentMessages_emptyConversation_returnsEmptyList() {
        ConversationId id = ConversationId.generate();
        Conversation conversation = Conversation.create(userId, tenantId, "New chat");
        when(conversationRepository.findById(id)).thenReturn(Optional.of(conversation));

        List<Message> result = adapter().getRecentMessages(id, tenantId, 10);

        assertThat(result).isEmpty();
    }
}
