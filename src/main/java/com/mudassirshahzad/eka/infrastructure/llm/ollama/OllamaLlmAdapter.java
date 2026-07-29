package com.mudassirshahzad.eka.infrastructure.llm.ollama;

import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.generation.model.FinishReason;
import com.mudassirshahzad.eka.domain.generation.model.GenerationOptions;
import com.mudassirshahzad.eka.domain.generation.model.LlmRequest;
import com.mudassirshahzad.eka.domain.generation.model.LlmResponse;
import com.mudassirshahzad.eka.domain.generation.model.PromptRequest;
import com.mudassirshahzad.eka.domain.generation.port.LlmPort;
import com.mudassirshahzad.eka.infrastructure.llm.exception.LlmProviderUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * {@link LlmPort} implementation backed by the Ollama chat model via Spring AI.
 *
 * <p>All Spring AI types are confined to this class. No Spring AI types appear in the domain or
 * application layers — conversion from {@link PromptRequest} to Spring AI {@link Prompt} and back
 * from {@link ChatResponse} to {@link LlmResponse} is the exclusive responsibility of this adapter.
 *
 * <p>Generation parameters ({@link GenerationOptions}) override the global defaults configured in
 * {@code application.yml}. The active model name is taken from {@link GenerationOptions#modelNameOverride()}
 * when present, otherwise from the {@code spring.ai.ollama.chat.model} property.
 *
 * <p><b>Retry (ADR G13 / P04.13.5 reconciliation):</b> transient failures from
 * {@link ChatModel#call(Prompt)} are retried up to {@value #MAX_RETRIES} times with exponential
 * backoff ({@value #INITIAL_BACKOFF_MS}ms initial, capped at {@value #MAX_BACKOFF_MS}ms) before
 * being wrapped as {@link LlmProviderUnavailableException}. This mirrors the retry shape already
 * established in {@code EmbeddingService.embedWithRetry} elsewhere in this codebase — a plain
 * bounded retry loop, not a resilience framework. Only the provider call itself is retried;
 * prompt construction is pure and retrying it would serve no purpose.
 *
 * <p>Logging: model name, finish reason, and token counts are logged at DEBUG; retry attempts are
 * logged at WARN. Prompt content, system text, and generated text are never logged.
 */
@Slf4j
@Component
public class OllamaLlmAdapter implements LlmPort {

    private static final long INITIAL_BACKOFF_MS = 100L;
    private static final long MAX_BACKOFF_MS     = 1000L;
    private static final int  MAX_RETRIES        = 3;

    private final ChatModel chatModel;
    private final String    defaultModelName;

    public OllamaLlmAdapter(
            ChatModel chatModel,
            @Value("${spring.ai.ollama.chat.model:qwen3}") String defaultModelName) {
        this.chatModel        = Objects.requireNonNull(chatModel,        "chatModel must not be null");
        this.defaultModelName = Objects.requireNonNull(defaultModelName, "defaultModelName must not be null");
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String activeModel = resolveModel(request.options());
        long   startNano   = System.nanoTime();

        try {
            Prompt       prompt   = buildPrompt(request.promptRequest(), request.options(), activeModel);
            ChatResponse response = callWithRetry(prompt);
            long         latency  = elapsedMs(startNano);

            LlmResponse result = toResponse(response, activeModel, latency);
            log.debug("LLM generation completed: model={} finishReason={} promptTokens={} completionTokens={} latencyMs={}",
                    result.modelName(), result.finishReason(),
                    result.promptTokens(), result.completionTokens(), result.latencyMs());
            return result;

        } catch (LlmProviderUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("LLM generation failed: model={} latencyMs={} reason={}",
                    activeModel, elapsedMs(startNano), ex.getMessage());
            throw new LlmProviderUnavailableException("LLM generation failed: " + ex.getMessage(), ex);
        }
    }

    private String resolveModel(GenerationOptions options) {
        return options.hasModelOverride() ? options.modelNameOverride() : defaultModelName;
    }

    private ChatResponse callWithRetry(Prompt prompt) {
        long             backoff       = INITIAL_BACKOFF_MS;
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return chatModel.call(prompt);
            } catch (RuntimeException ex) {
                lastException = ex;
                if (attempt < MAX_RETRIES) {
                    log.warn("LLM call attempt {}/{} failed, retrying in {}ms: {}",
                            attempt, MAX_RETRIES, backoff, ex.getMessage());
                    sleepBackoff(backoff);
                    backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
                }
            }
        }
        throw lastException;
    }

    private void sleepBackoff(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during LLM retry", ie);
        }
    }

    private Prompt buildPrompt(PromptRequest promptRequest, GenerationOptions options, String modelName) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(promptRequest.systemText()));

        for (Message msg : promptRequest.memoryMessages()) {
            messages.add(toSpringMessage(msg));
        }

        messages.add(new UserMessage(promptRequest.userText()));

        OllamaOptions ollamaOptions = OllamaOptions.builder()
                .model(modelName)
                .temperature(options.temperature())
                .numPredict(options.maxTokens())
                .topP(options.topP())
                .build();

        return new Prompt(messages, ollamaOptions);
    }

    private static org.springframework.ai.chat.messages.Message toSpringMessage(Message domainMessage) {
        return switch (domainMessage.role()) {
            case USER      -> new UserMessage(domainMessage.content());
            case ASSISTANT -> new AssistantMessage(domainMessage.content());
            case SYSTEM    -> new SystemMessage(domainMessage.content());
        };
    }

    private LlmResponse toResponse(ChatResponse response, String activeModel, long latencyMs) {
        String text          = extractText(response);
        FinishReason reason  = mapFinishReason(response);
        String modelName     = resolveResponseModel(response, activeModel);
        int promptTokens     = extractPromptTokens(response);
        int completionTokens = extractCompletionTokens(response);

        return new LlmResponse(text, reason, modelName, promptTokens, completionTokens, latencyMs);
    }

    private static String extractText(ChatResponse response) {
        try {
            String text = response.getResult().getOutput().getText();
            return text != null ? text : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static FinishReason mapFinishReason(ChatResponse response) {
        try {
            ChatGenerationMetadata meta = response.getResult().getMetadata();
            String raw = meta.getFinishReason();
            if (raw == null) return FinishReason.STOP;
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "stop"           -> FinishReason.STOP;
                case "length"         -> FinishReason.LENGTH;
                case "tool_calls"     -> FinishReason.TOOL_CALL;
                case "content_filter" -> FinishReason.CONTENT_FILTER;
                default               -> FinishReason.STOP;
            };
        } catch (Exception ignored) {
            return FinishReason.STOP;
        }
    }

    private static String resolveResponseModel(ChatResponse response, String activeModel) {
        try {
            String reported = response.getMetadata().getModel();
            return (reported != null && !reported.isBlank()) ? reported : activeModel;
        } catch (Exception ignored) {
            return activeModel;
        }
    }

    private static int extractPromptTokens(ChatResponse response) {
        try {
            Integer tokens = response.getMetadata().getUsage().getPromptTokens();
            return tokens != null ? tokens : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int extractCompletionTokens(ChatResponse response) {
        try {
            Integer tokens = response.getMetadata().getUsage().getCompletionTokens();
            return tokens != null ? tokens : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }
}
