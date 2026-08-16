package com.mudassirshahzad.eka.application.generation;

import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.conversation.Citation;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.document.DocumentId;
import com.mudassirshahzad.eka.domain.generation.exception.LlmException;
import com.mudassirshahzad.eka.domain.generation.model.FinishReason;
import com.mudassirshahzad.eka.domain.generation.model.GeneratedResponse;
import com.mudassirshahzad.eka.domain.generation.model.GenerationOptions;
import com.mudassirshahzad.eka.domain.generation.model.GuardrailResult;
import com.mudassirshahzad.eka.domain.generation.model.LlmRequest;
import com.mudassirshahzad.eka.domain.generation.model.LlmResponse;
import com.mudassirshahzad.eka.domain.generation.model.PromptBuildRequest;
import com.mudassirshahzad.eka.domain.generation.model.PromptRequest;
import com.mudassirshahzad.eka.domain.generation.port.CitationPort;
import com.mudassirshahzad.eka.domain.generation.port.ConversationHistoryPort;
import com.mudassirshahzad.eka.domain.generation.port.LlmPort;
import com.mudassirshahzad.eka.domain.generation.port.OutputGuardrailsPort;
import com.mudassirshahzad.eka.domain.generation.port.PromptBuilderPort;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledChunk;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledContext;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

    @Mock private PromptBuilderPort         promptBuilderPort;
    @Mock private LlmPort                   llmPort;
    @Mock private OutputGuardrailsPort      guardrailsPort;
    @Mock private CitationPort              citationPort;
    @Mock private ConversationHistoryPort   conversationHistoryPort;

    private GenerationService service;

    private final TenantId        tenantId       = TenantId.generate();
    private final DocumentId      docId          = DocumentId.generate();
    private final ConversationId  conversationId = ConversationId.generate();
    private final PromptRequest   prompt         = new PromptRequest("system", "user", List.of(), List.of());
    private final LlmResponse     llmResp        = new LlmResponse("The answer.", FinishReason.STOP, "qwen3", 100, 50, 300L);
    private final GuardrailResult passed         = GuardrailResult.pass("The answer.");
    private final GuardrailResult blocked        = GuardrailResult.block("I cannot answer that.");

    @BeforeEach
    void setUp() {
        service = new GenerationService(
                promptBuilderPort, llmPort, guardrailsPort,
                citationPort, conversationHistoryPort, ObservationRegistry.NOOP, 10);
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullPromptBuilderPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GenerationService(
                        null, llmPort, guardrailsPort, citationPort, conversationHistoryPort, ObservationRegistry.NOOP, 10))
                .withMessageContaining("promptBuilderPort");
    }

    @Test
    void constructor_rejectsNullLlmPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GenerationService(
                        promptBuilderPort, null, guardrailsPort, citationPort, conversationHistoryPort, ObservationRegistry.NOOP, 10))
                .withMessageContaining("llmPort");
    }

    @Test
    void constructor_rejectsNullGuardrailsPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GenerationService(
                        promptBuilderPort, llmPort, null, citationPort, conversationHistoryPort, ObservationRegistry.NOOP, 10))
                .withMessageContaining("guardrailsPort");
    }

    @Test
    void constructor_rejectsNullCitationPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GenerationService(
                        promptBuilderPort, llmPort, guardrailsPort, null, conversationHistoryPort, ObservationRegistry.NOOP, 10))
                .withMessageContaining("citationPort");
    }

    @Test
    void constructor_rejectsNullConversationHistoryPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GenerationService(
                        promptBuilderPort, llmPort, guardrailsPort, citationPort, null, ObservationRegistry.NOOP, 10))
                .withMessageContaining("conversationHistoryPort");
    }

    @Test
    void constructor_rejectsNullObservationRegistry() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GenerationService(
                        promptBuilderPort, llmPort, guardrailsPort, citationPort, conversationHistoryPort, null, 10))
                .withMessageContaining("observationRegistry");
    }

    @Test
    void constructor_rejectsNegativeMemoryWindowSize() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GenerationService(
                        promptBuilderPort, llmPort, guardrailsPort, citationPort, conversationHistoryPort, ObservationRegistry.NOOP, -1))
                .withMessageContaining("memoryWindowSize must be >= 0");
    }

    @Test
    void constructor_acceptsZeroMemoryWindowSize() {
        GenerationService svc = new GenerationService(
                promptBuilderPort, llmPort, guardrailsPort, citationPort, conversationHistoryPort, ObservationRegistry.NOOP, 0);
        assertThat(svc).isNotNull();
    }

    // ── Null guard on generate ────────────────────────────────────────────────

    @Test
    void generate_throwsOnNullRequest() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.generate(null))
                .withMessageContaining("request must not be null");
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void generate_returnsGeneratedText() {
        stubSuccessfulPipeline(List.of());

        GeneratedResponse result = service.generate(statelessRequest());

        assertThat(result.generatedText()).isEqualTo("The answer.");
    }

    @Test
    void generate_returnsModelName() {
        stubSuccessfulPipeline(List.of());

        GeneratedResponse result = service.generate(statelessRequest());

        assertThat(result.modelName()).isEqualTo("qwen3");
    }

    @Test
    void generate_returnsTotalTokens() {
        stubSuccessfulPipeline(List.of());

        GeneratedResponse result = service.generate(statelessRequest());

        assertThat(result.totalTokens()).isEqualTo(150);
    }

    @Test
    void generate_recordsLatencyMs() {
        stubSuccessfulPipeline(List.of());

        GeneratedResponse result = service.generate(statelessRequest());

        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void generate_returnsCitationsFromCitationPort() {
        Citation citation = Citation.of(ChunkId.generate(), 0.9);
        stubSuccessfulPipeline(List.of(citation));

        GeneratedResponse result = service.generate(statelessRequest());

        assertThat(result.citations()).containsExactly(citation);
    }

    // ── Port wiring ───────────────────────────────────────────────────────────

    @Test
    void generate_passesOriginalQueryTextToPromptBuilder() {
        stubSuccessfulPipeline(List.of());
        ArgumentCaptor<PromptBuildRequest> captor = ArgumentCaptor.forClass(PromptBuildRequest.class);

        service.generate(statelessRequest());

        verify(promptBuilderPort).build(captor.capture());
        assertThat(captor.getValue().originalQueryText()).isEqualTo("What is RAG?");
    }

    @Test
    void generate_passesAssembledContextToPromptBuilder() {
        AssembledContext ctx = contextWithOneChunk();
        stubSuccessfulPipeline(List.of());
        ArgumentCaptor<PromptBuildRequest> captor = ArgumentCaptor.forClass(PromptBuildRequest.class);

        service.generate(statelessRequest(ctx));

        verify(promptBuilderPort).build(captor.capture());
        assertThat(captor.getValue().assembledContext()).isSameAs(ctx);
    }

    @Test
    void generate_passesAssembledContextToCitationPort() {
        AssembledContext ctx = contextWithOneChunk();
        stubSuccessfulPipeline(List.of());

        service.generate(statelessRequest(ctx));

        verify(citationPort).resolve(any(), eq(ctx), eq(tenantId));
    }

    // ── Guardrails block ──────────────────────────────────────────────────────

    @Test
    void generate_returnsGuardrailFallbackTextWhenBlocked() {
        when(promptBuilderPort.build(any())).thenReturn(prompt);
        when(llmPort.generate(any())).thenReturn(llmResp);
        when(guardrailsPort.apply(any(), any())).thenReturn(blocked);
        when(citationPort.resolve(any(), any(), any())).thenReturn(List.of());

        GeneratedResponse result = service.generate(statelessRequest());

        assertThat(result.generatedText()).isEqualTo("I cannot answer that.");
    }

    @Test
    void generate_citationPortReceivesGuardrailText() {
        when(promptBuilderPort.build(any())).thenReturn(prompt);
        when(llmPort.generate(any())).thenReturn(llmResp);
        when(guardrailsPort.apply(any(), any())).thenReturn(blocked);
        when(citationPort.resolve(any(), any(), any())).thenReturn(List.of());
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);

        service.generate(statelessRequest());

        verify(citationPort).resolve(textCaptor.capture(), any(), any());
        assertThat(textCaptor.getValue()).isEqualTo("I cannot answer that.");
    }

    // ── LLM failure propagates ────────────────────────────────────────────────

    @Test
    void generate_propagatesLlmException() {
        when(promptBuilderPort.build(any())).thenReturn(prompt);
        when(llmPort.generate(any(LlmRequest.class))).thenThrow(new LlmException("Ollama down") {});

        assertThatThrownBy(() -> service.generate(statelessRequest()))
                .isInstanceOf(LlmException.class);
        verify(guardrailsPort, never()).apply(any(), any());
    }

    // ── Default options ───────────────────────────────────────────────────────

    @Test
    void generate_appliesDefaultOptionsWhenNullPassedInRequest() {
        GenerationRequest req = new GenerationRequest(emptyContext(), "What is RAG?", tenantId, null, null);
        stubSuccessfulPipeline(List.of());
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);

        service.generate(req);

        verify(llmPort).generate(captor.capture());
        assertThat(captor.getValue().options()).isEqualTo(GenerationOptions.DEFAULT);
    }

    // ── Conversation Memory (P04.10) ──────────────────────────────────────────

    @Test
    void generate_doesNotCallHistoryPort_whenConversationIdIsNull() {
        stubSuccessfulPipeline(List.of());

        service.generate(statelessRequest());

        verify(conversationHistoryPort, never()).getRecentMessages(any(), any(), anyInt());
    }

    @Test
    void generate_passesEmptyMemory_whenConversationIdIsNull() {
        stubSuccessfulPipeline(List.of());
        ArgumentCaptor<PromptBuildRequest> captor = ArgumentCaptor.forClass(PromptBuildRequest.class);

        service.generate(statelessRequest());

        verify(promptBuilderPort).build(captor.capture());
        assertThat(captor.getValue().memoryMessages()).isEmpty();
    }

    @Test
    void generate_callsHistoryPort_whenConversationIdPresent() {
        when(conversationHistoryPort.getRecentMessages(eq(conversationId), eq(tenantId), eq(10)))
                .thenReturn(List.of());
        stubSuccessfulPipelineNoHistoryStub(List.of());

        service.generate(conversationalRequest());

        verify(conversationHistoryPort).getRecentMessages(conversationId, tenantId, 10);
    }

    @Test
    void generate_populatesMemoryMessages_whenHistoryReturned() {
        Message userMsg      = Message.userMessage("Previous question");
        Message assistantMsg = Message.assistantMessage("Previous answer", List.of(), null);
        List<Message> history = List.of(userMsg, assistantMsg);

        when(conversationHistoryPort.getRecentMessages(eq(conversationId), eq(tenantId), eq(10)))
                .thenReturn(history);
        stubSuccessfulPipelineNoHistoryStub(List.of());
        ArgumentCaptor<PromptBuildRequest> captor = ArgumentCaptor.forClass(PromptBuildRequest.class);

        service.generate(conversationalRequest());

        verify(promptBuilderPort).build(captor.capture());
        assertThat(captor.getValue().memoryMessages()).hasSize(2);
        assertThat(captor.getValue().memoryMessages().get(0).content()).isEqualTo("Previous question");
        assertThat(captor.getValue().memoryMessages().get(1).content()).isEqualTo("Previous answer");
    }

    @Test
    void generate_passesEmptyMemory_whenHistoryPortReturnsEmpty() {
        when(conversationHistoryPort.getRecentMessages(eq(conversationId), eq(tenantId), eq(10)))
                .thenReturn(List.of());
        stubSuccessfulPipelineNoHistoryStub(List.of());
        ArgumentCaptor<PromptBuildRequest> captor = ArgumentCaptor.forClass(PromptBuildRequest.class);

        service.generate(conversationalRequest());

        verify(promptBuilderPort).build(captor.capture());
        assertThat(captor.getValue().memoryMessages()).isEmpty();
    }

    @Test
    void generate_preservesMemoryMessageOrder() {
        Message first  = Message.userMessage("first");
        Message second = Message.assistantMessage("second", List.of(), null);
        Message third  = Message.userMessage("third");

        when(conversationHistoryPort.getRecentMessages(eq(conversationId), eq(tenantId), eq(10)))
                .thenReturn(List.of(first, second, third));
        stubSuccessfulPipelineNoHistoryStub(List.of());
        ArgumentCaptor<PromptBuildRequest> captor = ArgumentCaptor.forClass(PromptBuildRequest.class);

        service.generate(conversationalRequest());

        verify(promptBuilderPort).build(captor.capture());
        List<Message> memory = captor.getValue().memoryMessages();
        assertThat(memory.get(0).content()).isEqualTo("first");
        assertThat(memory.get(1).content()).isEqualTo("second");
        assertThat(memory.get(2).content()).isEqualTo("third");
    }

    @Test
    void generate_passesMemoryWindowSizeToHistoryPort() {
        GenerationService smallWindowService = new GenerationService(
                promptBuilderPort, llmPort, guardrailsPort, citationPort, conversationHistoryPort, ObservationRegistry.NOOP, 3);

        when(conversationHistoryPort.getRecentMessages(eq(conversationId), eq(tenantId), eq(3)))
                .thenReturn(List.of());
        stubSuccessfulPipelineNoHistoryStub(List.of());

        smallWindowService.generate(conversationalRequest());

        verify(conversationHistoryPort).getRecentMessages(conversationId, tenantId, 3);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GenerationRequest statelessRequest() {
        return new GenerationRequest(emptyContext(), "What is RAG?", tenantId, GenerationOptions.DEFAULT, null);
    }

    private GenerationRequest statelessRequest(AssembledContext ctx) {
        return new GenerationRequest(ctx, "What is RAG?", tenantId, GenerationOptions.DEFAULT, null);
    }

    private GenerationRequest conversationalRequest() {
        return new GenerationRequest(emptyContext(), "What is RAG?", tenantId, GenerationOptions.DEFAULT, conversationId);
    }

    private AssembledContext emptyContext() {
        return new AssembledContext(List.of(), "What is RAG?", 4096, 0);
    }

    private AssembledContext contextWithOneChunk() {
        AssembledChunk chunk = new AssembledChunk(ChunkId.generate(), docId, tenantId, "RAG content", 0.9, 0);
        return new AssembledContext(List.of(chunk), "What is RAG?", 4096, 40);
    }

    private void stubSuccessfulPipeline(List<Citation> citations) {
        when(promptBuilderPort.build(any())).thenReturn(prompt);
        when(llmPort.generate(any())).thenReturn(llmResp);
        when(guardrailsPort.apply(any(), any())).thenReturn(passed);
        when(citationPort.resolve(any(), any(), any())).thenReturn(citations);
        // history port not called in stateless path — no stub needed
    }

    private void stubSuccessfulPipelineNoHistoryStub(List<Citation> citations) {
        when(promptBuilderPort.build(any())).thenReturn(prompt);
        when(llmPort.generate(any())).thenReturn(llmResp);
        when(guardrailsPort.apply(any(), any())).thenReturn(passed);
        when(citationPort.resolve(any(), any(), any())).thenReturn(citations);
    }
}
