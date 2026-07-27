package com.mudassir.eka.infrastructure.llm.ollama;

import com.mudassir.eka.domain.generation.model.FinishReason;
import com.mudassir.eka.domain.generation.model.GenerationOptions;
import com.mudassir.eka.domain.generation.model.LlmRequest;
import com.mudassir.eka.domain.generation.model.LlmResponse;
import com.mudassir.eka.domain.generation.model.PromptRequest;
import com.mudassir.eka.domain.generation.exception.LlmException;
import com.mudassir.eka.infrastructure.llm.exception.LlmProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaLlmAdapterTest {

    private static final String DEFAULT_MODEL = "qwen3";

    @Mock private ChatModel chatModel;

    private OllamaLlmAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OllamaLlmAdapter(chatModel, DEFAULT_MODEL);
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    @Test
    void constructor_rejectsNullChatModel() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OllamaLlmAdapter(null, DEFAULT_MODEL))
                .withMessageContaining("chatModel");
    }

    @Test
    void constructor_rejectsNullDefaultModelName() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OllamaLlmAdapter(chatModel, null))
                .withMessageContaining("defaultModelName");
    }

    // ── Null guard on generate ────────────────────────────────────────────────

    @Test
    void generate_throwsOnNullRequest() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.generate(null))
                .withMessageContaining("request must not be null");
    }

    // ── Successful generation ─────────────────────────────────────────────────

    @Test
    void generate_returnsGeneratedText() {
        stubResponse("RAG is a technique that combines retrieval and generation.", "stop", 100, 50, DEFAULT_MODEL);

        LlmResponse result = adapter.generate(defaultRequest());

        assertThat(result.generatedText()).isEqualTo("RAG is a technique that combines retrieval and generation.");
    }

    @Test
    void generate_mapsStopFinishReason() {
        stubResponse("answer", "stop", 100, 50, DEFAULT_MODEL);

        LlmResponse result = adapter.generate(defaultRequest());

        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void generate_mapsLengthFinishReason() {
        stubResponse("truncated answer", "length", 100, 2048, DEFAULT_MODEL);

        LlmResponse result = adapter.generate(defaultRequest());

        assertThat(result.finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void generate_mapsToolCallsFinishReason() {
        stubResponse("{\"tool\":\"search\"}", "tool_calls", 80, 40, DEFAULT_MODEL);

        LlmResponse result = adapter.generate(defaultRequest());

        assertThat(result.finishReason()).isEqualTo(FinishReason.TOOL_CALL);
    }

    @Test
    void generate_returnsTokenCounts() {
        stubResponse("answer", "stop", 123, 77, DEFAULT_MODEL);

        LlmResponse result = adapter.generate(defaultRequest());

        assertThat(result.promptTokens()).isEqualTo(123);
        assertThat(result.completionTokens()).isEqualTo(77);
        assertThat(result.totalTokens()).isEqualTo(200);
    }

    @Test
    void generate_recordsLatencyMs() {
        stubResponse("answer", "stop", 100, 50, DEFAULT_MODEL);

        LlmResponse result = adapter.generate(defaultRequest());

        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void generate_callsChatModelWithPrompt() {
        stubResponse("answer", "stop", 100, 50, DEFAULT_MODEL);
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);

        adapter.generate(defaultRequest());

        verify(chatModel).call(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    // ── Model name resolution ─────────────────────────────────────────────────

    @Test
    void generate_usesDefaultModelWhenNoOverride() {
        stubResponse("answer", "stop", 100, 50, DEFAULT_MODEL);

        LlmResponse result = adapter.generate(defaultRequest());

        assertThat(result.modelName()).isEqualTo(DEFAULT_MODEL);
    }

    @Test
    void generate_usesModelOverrideWhenSpecified() {
        var opts = new GenerationOptions(2048, 0.1, 1.0, "llama3.2");
        var request = new LlmRequest(promptRequest(), opts);
        stubResponse("answer", "stop", 100, 50, "llama3.2");

        LlmResponse result = adapter.generate(request);

        assertThat(result.modelName()).isEqualTo("llama3.2");
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void generate_wrapsProviderExceptionAsLlmException() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> adapter.generate(defaultRequest()))
                .isInstanceOf(LlmException.class)
                .isInstanceOf(LlmProviderUnavailableException.class)
                .hasMessageContaining("LLM generation failed");
    }

    // ── Unknown finish reason defaults to STOP ───────────────────────────────

    @Test
    void generate_unknownFinishReasonDefaultsToStop() {
        stubResponse("answer", "some_unknown_reason", 100, 50, DEFAULT_MODEL);

        LlmResponse result = adapter.generate(defaultRequest());

        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LlmRequest defaultRequest() {
        return new LlmRequest(promptRequest(), GenerationOptions.DEFAULT);
    }

    private static PromptRequest promptRequest() {
        return new PromptRequest("You are a helpful assistant.\n\nContext:\nSome relevant context.", "What is RAG?", List.of(), List.of());
    }

    private void stubResponse(String text, String finishReason, int promptTokens, int completionTokens, String modelName) {
        AssistantMessage output = mock(AssistantMessage.class);
        when(output.getText()).thenReturn(text);

        ChatGenerationMetadata genMeta = mock(ChatGenerationMetadata.class);
        when(genMeta.getFinishReason()).thenReturn(finishReason);

        Generation generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(output);
        when(generation.getMetadata()).thenReturn(genMeta);

        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(promptTokens);
        when(usage.getCompletionTokens()).thenReturn(completionTokens);

        ChatResponseMetadata responseMeta = mock(ChatResponseMetadata.class);
        when(responseMeta.getUsage()).thenReturn(usage);
        when(responseMeta.getModel()).thenReturn(modelName);

        ChatResponse response = mock(ChatResponse.class);
        when(response.getResult()).thenReturn(generation);
        when(response.getMetadata()).thenReturn(responseMeta);

        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }
}
