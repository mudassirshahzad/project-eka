package com.mudassirshahzad.eka.api.dto;

import com.mudassirshahzad.eka.application.orchestration.RagTurnResult;
import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.conversation.Citation;
import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.generation.model.GeneratedResponse;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DTO factory-method (domain -&gt; wire) mapping coverage for P05.1's REST layer. These are the
 * only places domain objects cross into API-visible shapes — {@code never expose domain models}
 * is enforced by construction (every field here is a primitive/JDK type), verified here by
 * checking the mapped values, not just that mapping compiles.
 */
class ApiDtoMappingTest {

    private final UserId   userId   = UserId.generate();
    private final TenantId tenantId = TenantId.generate();

    // ── CitationResponse ─────────────────────────────────────────────────────

    @Test
    void citationResponse_from_mapsChunkIdAndScore() {
        Citation citation = Citation.of(ChunkId.generate(), 0.83);

        CitationResponse response = CitationResponse.from(citation);

        assertThat(response.chunkId()).isEqualTo(citation.chunkId().value());
        assertThat(response.relevanceScore()).isEqualTo(0.83);
    }

    // ── MessageResponse ───────────────────────────────────────────────────────

    @Test
    void messageResponse_from_mapsRoleContentAndCitations() {
        Citation citation = Citation.of(ChunkId.generate(), 0.5);
        Message message = Message.assistantMessage("the answer", List.of(citation), null);

        MessageResponse response = MessageResponse.from(message);

        assertThat(response.id()).isEqualTo(message.id());
        assertThat(response.role()).isEqualTo("ASSISTANT");
        assertThat(response.content()).isEqualTo("the answer");
        assertThat(response.citations()).extracting(CitationResponse::chunkId)
                .containsExactly(citation.chunkId().value());
        assertThat(response.createdAt()).isEqualTo(message.createdAt());
    }

    @Test
    void messageResponse_from_noCitations_producesEmptyList() {
        Message message = Message.userMessage("hello");

        MessageResponse response = MessageResponse.from(message);

        assertThat(response.citations()).isEmpty();
    }

    // ── ConversationResponse ──────────────────────────────────────────────────

    @Test
    void conversationResponse_from_mapsIdTitleAndCreatedAt() {
        Conversation conversation = Conversation.create(userId, tenantId, "My chat");

        ConversationResponse response = ConversationResponse.from(conversation);

        assertThat(response.id()).isEqualTo(conversation.getId().value());
        assertThat(response.title()).isEqualTo("My chat");
        assertThat(response.createdAt()).isEqualTo(conversation.getCreatedAt());
    }

    // ── ConversationDetailResponse ────────────────────────────────────────────

    @Test
    void conversationDetailResponse_from_mapsMessagesInOrder() {
        Conversation conversation = Conversation.create(userId, tenantId, "chat");
        conversation.addMessage(Message.userMessage("first"));
        conversation.addMessage(Message.assistantMessage("second", List.of(), null));

        ConversationDetailResponse response = ConversationDetailResponse.from(conversation);

        assertThat(response.messages()).extracting(MessageResponse::content)
                .containsExactly("first", "second");
    }

    @Test
    void conversationDetailResponse_from_emptyConversation_producesEmptyMessageList() {
        Conversation conversation = Conversation.create(userId, tenantId, "new chat");

        ConversationDetailResponse response = ConversationDetailResponse.from(conversation);

        assertThat(response.messages()).isEmpty();
    }

    // ── GeneratedAnswerResponse ────────────────────────────────────────────────

    @Test
    void generatedAnswerResponse_from_mapsMessageAndGenerationMetadata() {
        Citation citation = Citation.of(ChunkId.generate(), 0.9);
        Message assistantMessage = Message.assistantMessage("cited answer", List.of(citation), null);
        GeneratedResponse generated = new GeneratedResponse("cited answer", List.of(citation), "qwen3", 123, 250L);
        RagTurnResult result = new RagTurnResult(assistantMessage, generated);

        GeneratedAnswerResponse response = GeneratedAnswerResponse.from(result);

        assertThat(response.messageId()).isEqualTo(assistantMessage.id());
        assertThat(response.content()).isEqualTo("cited answer");
        assertThat(response.citations()).extracting(CitationResponse::chunkId)
                .containsExactly(citation.chunkId().value());
        assertThat(response.modelName()).isEqualTo("qwen3");
        assertThat(response.totalTokens()).isEqualTo(123);
        assertThat(response.latencyMs()).isEqualTo(250L);
        assertThat(response.createdAt()).isEqualTo(assistantMessage.createdAt());
    }
}
