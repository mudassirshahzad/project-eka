package com.mudassirshahzad.eka.application.orchestration;

import com.mudassirshahzad.eka.application.conversation.AddAssistantMessageCommand;
import com.mudassirshahzad.eka.application.conversation.AddUserMessageCommand;
import com.mudassirshahzad.eka.application.conversation.ConversationApplicationService;
import com.mudassirshahzad.eka.application.generation.GenerationRequest;
import com.mudassirshahzad.eka.application.generation.GenerationService;
import com.mudassirshahzad.eka.application.retrieval.RetrievalRequest;
import com.mudassirshahzad.eka.application.retrieval.RetrievalService;
import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.conversation.Citation;
import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.generation.model.GeneratedResponse;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledContext;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievalResult;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievedChunk;
import com.mudassirshahzad.eka.domain.retrieval.model.SearchMetadata;
import com.mudassirshahzad.eka.domain.retrieval.port.ContextAssemblyPort;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagOrchestrationServiceTest {

    private static final int TOKEN_BUDGET = 4096;

    @Mock private ConversationApplicationService conversationApplicationService;
    @Mock private RetrievalService                retrievalService;
    @Mock private ContextAssemblyPort              contextAssemblyPort;
    @Mock private GenerationService                generationService;

    private RagOrchestrationService orchestrator;

    private final ConversationId conversationId = ConversationId.generate();
    private final UserId         userId         = UserId.generate();
    private final TenantId       tenantId       = TenantId.generate();

    @BeforeEach
    void setUp() {
        orchestrator = new RagOrchestrationService(
                conversationApplicationService, retrievalService, contextAssemblyPort, generationService, TOKEN_BUDGET);
    }

    // ── Constructor guards ────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullConversationApplicationService() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RagOrchestrationService(
                        null, retrievalService, contextAssemblyPort, generationService, TOKEN_BUDGET))
                .withMessageContaining("conversationApplicationService");
    }

    @Test
    void constructor_rejectsNullRetrievalService() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RagOrchestrationService(
                        conversationApplicationService, null, contextAssemblyPort, generationService, TOKEN_BUDGET))
                .withMessageContaining("retrievalService");
    }

    @Test
    void constructor_rejectsNullContextAssemblyPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RagOrchestrationService(
                        conversationApplicationService, retrievalService, null, generationService, TOKEN_BUDGET))
                .withMessageContaining("contextAssemblyPort");
    }

    @Test
    void constructor_rejectsNullGenerationService() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RagOrchestrationService(
                        conversationApplicationService, retrievalService, contextAssemblyPort, null, TOKEN_BUDGET))
                .withMessageContaining("generationService");
    }

    @Test
    void constructor_rejectsNonPositiveTokenBudget() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new RagOrchestrationService(
                        conversationApplicationService, retrievalService, contextAssemblyPort, generationService, 0))
                .withMessageContaining("contextTokenBudget");
    }

    // ── Null guard on handleUserMessage ───────────────────────────────────────

    @Test
    void handleUserMessage_rejectsNullCommand() {
        assertThatNullPointerException()
                .isThrownBy(() -> orchestrator.handleUserMessage(null));
    }

    // ── Happy path — call sequence and data flow ──────────────────────────────

    @Test
    void handleUserMessage_persistsUserMessageFirst() {
        stubHappyPath();
        SendMessageCommand cmd = new SendMessageCommand(conversationId, userId, tenantId, "What is RAG?");

        orchestrator.handleUserMessage(cmd);

        ArgumentCaptor<AddUserMessageCommand> captor = ArgumentCaptor.forClass(AddUserMessageCommand.class);
        verify(conversationApplicationService).addUserMessage(captor.capture());
        assertThat(captor.getValue().conversationId()).isEqualTo(conversationId);
        assertThat(captor.getValue().userId()).isEqualTo(userId);
        assertThat(captor.getValue().content()).isEqualTo("What is RAG?");
    }

    @Test
    void handleUserMessage_retrievesWithOriginalUserContent() {
        stubHappyPath();
        SendMessageCommand cmd = new SendMessageCommand(conversationId, userId, tenantId, "What is RAG?");

        orchestrator.handleUserMessage(cmd);

        ArgumentCaptor<RetrievalRequest> captor = ArgumentCaptor.forClass(RetrievalRequest.class);
        verify(retrievalService).retrieve(captor.capture());
        assertThat(captor.getValue().queryText()).isEqualTo("What is RAG?");
        assertThat(captor.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().userId()).isEqualTo(userId);
    }

    @Test
    void handleUserMessage_assemblesContextUsingRetrievalsEffectiveQueryText() {
        RetrievedChunk chunk = sampleChunk();
        RetrievalResult retrievalResult = new RetrievalResult(
                List.of(chunk), new SearchMetadata(1, 5L, "hybrid"), "rewritten query");
        when(retrievalService.retrieve(any())).thenReturn(retrievalResult);
        AssembledContext assembledContext = emptyAssembledContext();
        when(contextAssemblyPort.assemble(any(), any(), anyInt())).thenReturn(assembledContext);
        stubGenerationAndPersistence();

        orchestrator.handleUserMessage(new SendMessageCommand(conversationId, userId, tenantId, "SLA?"));

        verify(contextAssemblyPort).assemble(eq(List.of(chunk)), eq("rewritten query"), eq(TOKEN_BUDGET));
    }

    @Test
    void handleUserMessage_generatesWithOriginalUserContentAndAssembledContext() {
        stubHappyPath();
        SendMessageCommand cmd = new SendMessageCommand(conversationId, userId, tenantId, "What is RAG?");

        orchestrator.handleUserMessage(cmd);

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationService).generate(captor.capture());
        assertThat(captor.getValue().originalQueryText()).isEqualTo("What is RAG?");
        assertThat(captor.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().conversationId()).isEqualTo(conversationId);
    }

    @Test
    void handleUserMessage_persistsAssistantReplyWithGeneratedTextAndCitations() {
        Citation citation = Citation.of(ChunkId.generate(), 0.77);
        GeneratedResponse generated = new GeneratedResponse(
                "the answer", List.of(citation), "qwen3", 42, 100L);
        stubUpToGeneration(generated);
        when(conversationApplicationService.addAssistantMessage(any()))
                .thenReturn(conversationWithAssistantMessage("the answer", List.of(citation)));

        orchestrator.handleUserMessage(new SendMessageCommand(conversationId, userId, tenantId, "question"));

        ArgumentCaptor<AddAssistantMessageCommand> captor = ArgumentCaptor.forClass(AddAssistantMessageCommand.class);
        verify(conversationApplicationService).addAssistantMessage(captor.capture());
        assertThat(captor.getValue().content()).isEqualTo("the answer");
        assertThat(captor.getValue().citations()).containsExactly(citation);
        assertThat(captor.getValue().conversationId()).isEqualTo(conversationId);
        assertThat(captor.getValue().userId()).isEqualTo(userId);
    }

    @Test
    void handleUserMessage_returnsResultWithPersistedMessageAndGeneratedResponse() {
        GeneratedResponse generated = new GeneratedResponse("answer text", List.of(), "qwen3", 10, 5L);
        stubUpToGeneration(generated);
        when(conversationApplicationService.addAssistantMessage(any()))
                .thenReturn(conversationWithAssistantMessage("answer text", List.of()));

        RagTurnResult result = orchestrator.handleUserMessage(
                new SendMessageCommand(conversationId, userId, tenantId, "question"));

        assertThat(result.assistantMessage().content()).isEqualTo("answer text");
        assertThat(result.generatedResponse()).isSameAs(generated);
    }

    @Test
    void handleUserMessage_callsCollaboratorsInOrder() {
        stubHappyPath();

        orchestrator.handleUserMessage(new SendMessageCommand(conversationId, userId, tenantId, "question"));

        InOrder order = inOrder(conversationApplicationService, retrievalService, contextAssemblyPort, generationService);
        order.verify(conversationApplicationService).addUserMessage(any());
        order.verify(retrievalService).retrieve(any());
        order.verify(contextAssemblyPort).assemble(any(), any(), anyInt());
        order.verify(generationService).generate(any());
        order.verify(conversationApplicationService).addAssistantMessage(any());
    }

    // ── Failure propagation — no assistant message persisted on upstream failure ─

    @Test
    void handleUserMessage_generationFailure_doesNotPersistAssistantMessage() {
        RetrievalResult retrievalResult = RetrievalResult.empty("hybrid", 1L, "question");
        when(retrievalService.retrieve(any())).thenReturn(retrievalResult);
        AssembledContext assembledContext = emptyAssembledContext();
        when(contextAssemblyPort.assemble(any(), any(), anyInt())).thenReturn(assembledContext);
        when(generationService.generate(any())).thenThrow(new RuntimeException("LLM unavailable"));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> orchestrator.handleUserMessage(
                        new SendMessageCommand(conversationId, userId, tenantId, "question")));

        verify(conversationApplicationService, never()).addAssistantMessage(any());
    }

    @Test
    void handleUserMessage_retrievalFailure_neverReachesGenerationOrPersistence() {
        when(retrievalService.retrieve(any())).thenThrow(new RuntimeException("retrieval down"));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> orchestrator.handleUserMessage(
                        new SendMessageCommand(conversationId, userId, tenantId, "question")));

        verifyNoInteractions(contextAssemblyPort, generationService);
        verify(conversationApplicationService, never()).addAssistantMessage(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubHappyPath() {
        GeneratedResponse generated = new GeneratedResponse("answer", List.of(), "qwen3", 10, 5L);
        stubUpToGeneration(generated);
        when(conversationApplicationService.addAssistantMessage(any()))
                .thenReturn(conversationWithAssistantMessage("answer", List.of()));
    }

    private void stubUpToGeneration(GeneratedResponse generated) {
        RetrievalResult retrievalResult = RetrievalResult.empty("hybrid", 1L, "question");
        when(retrievalService.retrieve(any())).thenReturn(retrievalResult);
        AssembledContext assembledContext = emptyAssembledContext();
        when(contextAssemblyPort.assemble(any(), any(), anyInt())).thenReturn(assembledContext);
        when(generationService.generate(any())).thenReturn(generated);
    }

    private void stubGenerationAndPersistence() {
        GeneratedResponse generated = new GeneratedResponse("answer", List.of(), "qwen3", 10, 5L);
        when(generationService.generate(any())).thenReturn(generated);
        when(conversationApplicationService.addAssistantMessage(any()))
                .thenReturn(conversationWithAssistantMessage("answer", List.of()));
    }

    private Conversation conversationWithAssistantMessage(String content, List<Citation> citations) {
        Conversation conversation = Conversation.create(userId, tenantId, "chat");
        conversation.addMessage(Message.assistantMessage(content, citations, null));
        return conversation;
    }

    private AssembledContext emptyAssembledContext() {
        return new AssembledContext(List.of(), "question", TOKEN_BUDGET, 0);
    }

    private RetrievedChunk sampleChunk() {
        return new RetrievedChunk(
                ChunkId.generate(), DocumentId.generate(), tenantId, "content", 0.9, 0);
    }

}
