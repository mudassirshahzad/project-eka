package com.mudassir.eka.application.generation;

import com.mudassir.eka.domain.chunk.ChunkId;
import com.mudassir.eka.domain.conversation.Citation;
import com.mudassir.eka.domain.document.DocumentId;
import com.mudassir.eka.domain.generation.exception.LlmException;
import com.mudassir.eka.domain.generation.model.FinishReason;
import com.mudassir.eka.domain.generation.model.GeneratedResponse;
import com.mudassir.eka.domain.generation.model.GenerationOptions;
import com.mudassir.eka.domain.generation.model.GuardrailResult;
import com.mudassir.eka.domain.generation.model.LlmRequest;
import com.mudassir.eka.domain.generation.model.LlmResponse;
import com.mudassir.eka.domain.generation.model.PromptBuildRequest;
import com.mudassir.eka.domain.generation.model.PromptRequest;
import com.mudassir.eka.domain.generation.port.CitationPort;
import com.mudassir.eka.domain.generation.port.LlmPort;
import com.mudassir.eka.domain.generation.port.OutputGuardrailsPort;
import com.mudassir.eka.domain.generation.port.PromptBuilderPort;
import com.mudassir.eka.domain.retrieval.model.AssembledChunk;
import com.mudassir.eka.domain.retrieval.model.AssembledContext;
import com.mudassir.eka.domain.shared.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

    @Mock private PromptBuilderPort    promptBuilderPort;
    @Mock private LlmPort              llmPort;
    @Mock private OutputGuardrailsPort guardrailsPort;
    @Mock private CitationPort         citationPort;

    private GenerationService service;

    private final TenantId        tenantId  = TenantId.generate();
    private final DocumentId      docId     = DocumentId.generate();
    private final PromptRequest   prompt    = new PromptRequest("system", "user", List.of(), List.of());
    private final LlmResponse     llmResp   = new LlmResponse("The answer.", FinishReason.STOP, "qwen3", 100, 50, 300L);
    private final GuardrailResult passed    = GuardrailResult.pass("The answer.");
    private final GuardrailResult blocked   = GuardrailResult.block("I cannot answer that.");

    @BeforeEach
    void setUp() {
        service = new GenerationService(promptBuilderPort, llmPort, guardrailsPort, citationPort);
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullPromptBuilderPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GenerationService(null, llmPort, guardrailsPort, citationPort))
                .withMessageContaining("promptBuilderPort");
    }

    @Test
    void constructor_rejectsNullLlmPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GenerationService(promptBuilderPort, null, guardrailsPort, citationPort))
                .withMessageContaining("llmPort");
    }

    @Test
    void constructor_rejectsNullGuardrailsPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GenerationService(promptBuilderPort, llmPort, null, citationPort))
                .withMessageContaining("guardrailsPort");
    }

    @Test
    void constructor_rejectsNullCitationPort() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GenerationService(promptBuilderPort, llmPort, guardrailsPort, null))
                .withMessageContaining("citationPort");
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

        GeneratedResponse result = service.generate(request());

        assertThat(result.generatedText()).isEqualTo("The answer.");
    }

    @Test
    void generate_returnsModelName() {
        stubSuccessfulPipeline(List.of());

        GeneratedResponse result = service.generate(request());

        assertThat(result.modelName()).isEqualTo("qwen3");
    }

    @Test
    void generate_returnsTotalTokens() {
        stubSuccessfulPipeline(List.of());

        GeneratedResponse result = service.generate(request());

        assertThat(result.totalTokens()).isEqualTo(150);
    }

    @Test
    void generate_recordsLatencyMs() {
        stubSuccessfulPipeline(List.of());

        GeneratedResponse result = service.generate(request());

        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void generate_returnsCitationsFromCitationPort() {
        Citation citation = Citation.of(ChunkId.generate(), 0.9);
        stubSuccessfulPipeline(List.of(citation));

        GeneratedResponse result = service.generate(request());

        assertThat(result.citations()).containsExactly(citation);
    }

    // ── Port wiring ───────────────────────────────────────────────────────────

    @Test
    void generate_passesOriginalQueryTextToPromptBuilder() {
        stubSuccessfulPipeline(List.of());
        ArgumentCaptor<PromptBuildRequest> captor = ArgumentCaptor.forClass(PromptBuildRequest.class);

        service.generate(request());

        verify(promptBuilderPort).build(captor.capture());
        assertThat(captor.getValue().originalQueryText()).isEqualTo("What is RAG?");
    }

    @Test
    void generate_passesAssembledContextToPromptBuilder() {
        AssembledContext ctx = contextWithOneChunk();
        stubSuccessfulPipeline(List.of());
        ArgumentCaptor<PromptBuildRequest> captor = ArgumentCaptor.forClass(PromptBuildRequest.class);

        service.generate(request(ctx));

        verify(promptBuilderPort).build(captor.capture());
        assertThat(captor.getValue().assembledContext()).isSameAs(ctx);
    }

    @Test
    void generate_passesAssembledContextToCitationPort() {
        AssembledContext ctx = contextWithOneChunk();
        stubSuccessfulPipeline(List.of());

        service.generate(request(ctx));

        verify(citationPort).resolve(any(), eq(ctx), eq(tenantId));
    }

    // ── Guardrails block ──────────────────────────────────────────────────────

    @Test
    void generate_returnsGuardrailFallbackTextWhenBlocked() {
        when(promptBuilderPort.build(any())).thenReturn(prompt);
        when(llmPort.generate(any())).thenReturn(llmResp);
        when(guardrailsPort.apply(any(), any())).thenReturn(blocked);
        when(citationPort.resolve(any(), any(), any())).thenReturn(List.of());

        GeneratedResponse result = service.generate(request());

        assertThat(result.generatedText()).isEqualTo("I cannot answer that.");
    }

    @Test
    void generate_citationPortReceivesGuardrailText() {
        when(promptBuilderPort.build(any())).thenReturn(prompt);
        when(llmPort.generate(any())).thenReturn(llmResp);
        when(guardrailsPort.apply(any(), any())).thenReturn(blocked);
        when(citationPort.resolve(any(), any(), any())).thenReturn(List.of());
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);

        service.generate(request());

        verify(citationPort).resolve(textCaptor.capture(), any(), any());
        assertThat(textCaptor.getValue()).isEqualTo("I cannot answer that.");
    }

    // ── LLM failure propagates ────────────────────────────────────────────────

    @Test
    void generate_propagatesLlmException() {
        when(promptBuilderPort.build(any())).thenReturn(prompt);
        when(llmPort.generate(any(LlmRequest.class))).thenThrow(new LlmException("Ollama down") {});

        assertThatThrownBy(() -> service.generate(request()))
                .isInstanceOf(LlmException.class);
        verify(guardrailsPort, never()).apply(any(), any());
    }

    // ── Default options are applied when null ─────────────────────────────────

    @Test
    void generate_appliesDefaultOptionsWhenNullPassedInRequest() {
        GenerationRequest req = new GenerationRequest(emptyContext(), "What is RAG?", tenantId, null);
        stubSuccessfulPipeline(List.of());
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);

        service.generate(req);

        verify(llmPort).generate(captor.capture());
        assertThat(captor.getValue().options()).isEqualTo(GenerationOptions.DEFAULT);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GenerationRequest request() {
        return new GenerationRequest(emptyContext(), "What is RAG?", tenantId, GenerationOptions.DEFAULT);
    }

    private GenerationRequest request(AssembledContext ctx) {
        return new GenerationRequest(ctx, "What is RAG?", tenantId, GenerationOptions.DEFAULT);
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
    }
}
