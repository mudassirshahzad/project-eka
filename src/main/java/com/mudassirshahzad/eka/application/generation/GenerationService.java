package com.mudassirshahzad.eka.application.generation;

import com.mudassirshahzad.eka.domain.conversation.Citation;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.generation.model.GeneratedResponse;
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
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates the full text generation pipeline:
 * Conversation History → Prompt Builder → LLM → Output Guardrails → Citation Resolution.
 *
 * <p>No Spring AI types appear here — all AI provider interaction is confined to
 * {@code OllamaLlmAdapter} in the infrastructure layer. This service is a pure orchestrator:
 * it calls ports in sequence and assembles the final {@link GeneratedResponse}.
 *
 * <p>Conversation Memory (P04.10): when a {@code conversationId} is present on the
 * {@link GenerationRequest}, recent messages are fetched via {@link ConversationHistoryPort}
 * and injected into the prompt via {@link PromptBuildRequest#memoryMessages()}.
 * When {@code conversationId} is null, generation proceeds statelessly (ADR M02).
 *
 * <p>Memory messages and tools are controlled by P04.10 and the agent milestone respectively.
 * Tools remain an empty passthrough today.
 *
 * <p>Logging: model name, finish reason, token counts, and memory window size are logged at DEBUG.
 * Generated text and prompt content are never logged.
 */
@Slf4j
@Service
public class GenerationService {

    private final PromptBuilderPort         promptBuilderPort;
    private final LlmPort                   llmPort;
    private final OutputGuardrailsPort      guardrailsPort;
    private final CitationPort              citationPort;
    private final ConversationHistoryPort   conversationHistoryPort;
    private final ObservationRegistry       observationRegistry;
    private final int                       memoryWindowSize;

    public GenerationService(
            PromptBuilderPort        promptBuilderPort,
            LlmPort                  llmPort,
            OutputGuardrailsPort     guardrailsPort,
            CitationPort             citationPort,
            ConversationHistoryPort  conversationHistoryPort,
            ObservationRegistry      observationRegistry,
            @Value("${app.conversation.memory-window-size:10}") int memoryWindowSize) {
        this.promptBuilderPort       = Objects.requireNonNull(promptBuilderPort,       "promptBuilderPort must not be null");
        this.llmPort                 = Objects.requireNonNull(llmPort,                 "llmPort must not be null");
        this.guardrailsPort          = Objects.requireNonNull(guardrailsPort,          "guardrailsPort must not be null");
        this.citationPort            = Objects.requireNonNull(citationPort,            "citationPort must not be null");
        this.conversationHistoryPort = Objects.requireNonNull(conversationHistoryPort, "conversationHistoryPort must not be null");
        this.observationRegistry     = Objects.requireNonNull(observationRegistry,     "observationRegistry must not be null");
        if (memoryWindowSize < 0)
            throw new IllegalArgumentException("memoryWindowSize must be >= 0 but was " + memoryWindowSize);
        this.memoryWindowSize = memoryWindowSize;
    }

    /**
     * Runs inside an {@code eka.generation} {@link Observation} (P05.4, ADR OB02) — a Micrometer
     * timer today, trace-ready with zero code changes once distributed tracing is added.
     */
    public GeneratedResponse generate(GenerationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return Observation.createNotStarted("eka.generation", observationRegistry)
                .observe(() -> doGenerate(request));
    }

    private GeneratedResponse doGenerate(GenerationRequest request) {
        long start = System.nanoTime();

        // 1. Retrieve conversation history (empty when conversationId is null — ADR M02)
        List<Message> history = fetchHistory(request);

        log.debug("Generation started: tenant={} memoryMessages={} hasConversation={}",
                request.tenantId(), history.size(), request.conversationId() != null);

        // 2. Build prompt — history now populates memoryMessages
        PromptBuildRequest buildRequest = new PromptBuildRequest(
                request.assembledContext(),
                request.originalQueryText(),
                history,
                List.of(),
                request.tenantId());

        PromptRequest promptRequest = promptBuilderPort.build(buildRequest);

        // 3. Generate with LLM
        LlmRequest  llmRequest  = new LlmRequest(promptRequest, request.options());
        LlmResponse llmResponse = llmPort.generate(llmRequest);

        log.debug("LLM response received: tenant={} model={} finishReason={} tokens={} latencyMs={}",
                request.tenantId(), llmResponse.modelName(),
                llmResponse.finishReason(), llmResponse.totalTokens(), llmResponse.latencyMs());

        // 4. Apply output guardrails
        GuardrailResult guardrailResult = guardrailsPort.apply(llmResponse.generatedText(), request.tenantId());

        if (guardrailResult.isBlocked()) {
            log.warn("Output guardrails blocked LLM response: tenant={}", request.tenantId());
        }

        // 5. Resolve citations — AssembledContext is carried as a read-only passenger
        List<Citation> citations = citationPort.resolve(
                guardrailResult.text(),
                request.assembledContext(),
                request.tenantId());

        return new GeneratedResponse(
                guardrailResult.text(),
                citations,
                llmResponse.modelName(),
                llmResponse.totalTokens(),
                elapsedMs(start));
    }

    private List<Message> fetchHistory(GenerationRequest request) {
        if (request.conversationId() == null) {
            return List.of();
        }
        return conversationHistoryPort.getRecentMessages(
                request.conversationId(),
                request.tenantId(),
                memoryWindowSize);
    }

    private static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }
}
