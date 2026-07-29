package com.mudassirshahzad.eka.infrastructure.conversation;

import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class InMemoryConversationHistoryAdapterTest {

    private InMemoryConversationHistoryAdapter adapter;

    private final TenantId        tenantId = TenantId.generate();
    private final ConversationId  convId   = ConversationId.generate();

    @BeforeEach
    void setUp() {
        adapter = new InMemoryConversationHistoryAdapter();
    }

    // ── Null guards ───────────────────────────────────────────────────────────

    @Test
    void getRecentMessages_throwsOnNullConversationId() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.getRecentMessages(null, tenantId, 10))
                .withMessageContaining("conversationId");
    }

    @Test
    void getRecentMessages_throwsOnNullTenantId() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.getRecentMessages(convId, null, 10))
                .withMessageContaining("tenantId");
    }

    @Test
    void getRecentMessages_throwsOnNegativeMax() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> adapter.getRecentMessages(convId, tenantId, -1))
                .withMessageContaining("maxMessages must be >= 0");
    }

    // ── Empty / missing conversation ──────────────────────────────────────────

    @Test
    void getRecentMessages_returnsEmpty_whenConversationNotFound() {
        List<Message> result = adapter.getRecentMessages(ConversationId.generate(), tenantId, 10);
        assertThat(result).isEmpty();
    }

    @Test
    void getRecentMessages_returnsEmpty_whenMaxIsZero() {
        adapter.addMessage(convId, Message.userMessage("hello"));
        List<Message> result = adapter.getRecentMessages(convId, tenantId, 0);
        assertThat(result).isEmpty();
    }

    // ── Single message ────────────────────────────────────────────────────────

    @Test
    void getRecentMessages_returnsSingleMessage() {
        Message msg = Message.userMessage("What is RAG?");
        adapter.addMessage(convId, msg);

        List<Message> result = adapter.getRecentMessages(convId, tenantId, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("What is RAG?");
    }

    // ── Multiple messages ─────────────────────────────────────────────────────

    @Test
    void getRecentMessages_returnsAllMessages_whenCountBelowMax() {
        adapter.addMessage(convId, Message.userMessage("First question"));
        adapter.addMessage(convId, Message.assistantMessage("First answer", List.of(), null));

        List<Message> result = adapter.getRecentMessages(convId, tenantId, 10);

        assertThat(result).hasSize(2);
    }

    @Test
    void getRecentMessages_returnsLastN_whenExceedingMax() {
        for (int i = 1; i <= 5; i++) {
            adapter.addMessage(convId, Message.userMessage("Question " + i));
        }

        List<Message> result = adapter.getRecentMessages(convId, tenantId, 3);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).content()).isEqualTo("Question 3");
        assertThat(result.get(1).content()).isEqualTo("Question 4");
        assertThat(result.get(2).content()).isEqualTo("Question 5");
    }

    @Test
    void getRecentMessages_preservesChronologicalOrder() {
        Message user1      = Message.userMessage("user turn 1");
        Message assistant1 = Message.assistantMessage("assistant turn 1", List.of(), null);
        Message user2      = Message.userMessage("user turn 2");

        adapter.addMessage(convId, user1);
        adapter.addMessage(convId, assistant1);
        adapter.addMessage(convId, user2);

        List<Message> result = adapter.getRecentMessages(convId, tenantId, 10);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).content()).isEqualTo("user turn 1");
        assertThat(result.get(1).content()).isEqualTo("assistant turn 1");
        assertThat(result.get(2).content()).isEqualTo("user turn 2");
    }

    // ── Result is unmodifiable ────────────────────────────────────────────────

    @Test
    void getRecentMessages_returnsUnmodifiableList() {
        adapter.addMessage(convId, Message.userMessage("hello"));
        List<Message> result = adapter.getRecentMessages(convId, tenantId, 10);

        assertThat(result).isUnmodifiable();
    }

    // ── Conversation isolation ────────────────────────────────────────────────

    @Test
    void getRecentMessages_isolatesConversations() {
        ConversationId other = ConversationId.generate();

        adapter.addMessage(convId,  Message.userMessage("for conv A"));
        adapter.addMessage(other,   Message.userMessage("for conv B"));

        List<Message> resultA = adapter.getRecentMessages(convId,  tenantId, 10);
        List<Message> resultB = adapter.getRecentMessages(other,   tenantId, 10);

        assertThat(resultA).hasSize(1);
        assertThat(resultA.get(0).content()).isEqualTo("for conv A");
        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).content()).isEqualTo("for conv B");
    }

    // ── clearConversation ─────────────────────────────────────────────────────

    @Test
    void clearConversation_removesAllMessages() {
        adapter.addMessage(convId, Message.userMessage("first"));
        adapter.addMessage(convId, Message.userMessage("second"));

        adapter.clearConversation(convId);

        assertThat(adapter.getRecentMessages(convId, tenantId, 10)).isEmpty();
    }

    @Test
    void clearConversation_doesNotAffectOtherConversations() {
        ConversationId other = ConversationId.generate();
        adapter.addMessage(convId, Message.userMessage("stays"));
        adapter.addMessage(other,  Message.userMessage("cleared"));

        adapter.clearConversation(other);

        assertThat(adapter.getRecentMessages(convId,  tenantId, 10)).hasSize(1);
        assertThat(adapter.getRecentMessages(other,   tenantId, 10)).isEmpty();
    }
}
