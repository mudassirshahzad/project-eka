package com.mudassir.eka.application.generation;

import com.mudassir.eka.domain.conversation.Citation;
import com.mudassir.eka.domain.generation.model.GeneratedResponse;
import com.mudassir.eka.domain.generation.model.GuardrailResult;
import com.mudassir.eka.domain.generation.model.LlmRequest;
import com.mudassir.eka.domain.generation.model.LlmResponse;
import com.mudassir.eka.domain.generation.model.PromptBuildRequest;
import com.mudassir.eka.domain.generation.model.PromptRequest;
import com.mudassir.eka.domain.generation.port.CitationPort;
import com.mudassir.eka.domain.generation.port.LlmPort;
import com.mudassir.eka.domain.generation.port.OutputGuardrailsPort;
import com.mudassir.eka.domain.generation.port.PromptBuilderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates the full text generation pipeline:
 * Prompt Builder → LLM → Output Guardrails → Citation Resolution.
 *
 * <p>No Spring AI types appear here — all AI provider interaction is confined to
 * {@code OllamaLlmAdapter} in the infrastructure layer. This service is a pure orchestrator:
 * it calls ports in sequence and assembles the final {@link GeneratedResponse}.
 *
 * <p>Memory messages and tools are empty in P04.9. They will be populated starting P04.10
 * (conversation memory) and the agent milestone respectively, without changing this method's
 * signature.
 *
 * <p>Logging: model name, finish reason, and token counts are logged at DEBUG.
 * Generated text and prompt content are never logged.
 */
@Slf4j
@Service
public class GenerationService {

    private final PromptBuilderPort    promptBuilderPort;
    private final LlmPort              llmPort;
    private final OutputGuardrailsPort guardrailsPort;
    private final CitationPort         citationPort;

    public GenerationService(
            PromptBuilderPort    promptBuilderPort,
            LlmPort              llmPort,
            OutputGuardrailsPort guardrailsPort,
            CitationPort         citationPort) {
        this.promptBuilderPort = Objects.requireNonNull(promptBuilderPort, "promptBuilderPort must not be null");
        this.llmPort           = Objects.requireNonNull(llmPort,           "llmPort must not be null");
        this.guardrailsPort    = Objects.requireNonNull(guardrailsPort,    "guardrailsPort must not be null");
        this.citationPort      = Objects.requireNonNull(citationPort,      "citationPort must not be null");
    }

    public GeneratedResponse generate(GenerationRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        long start = System.nanoTime();

        // 1. Build prompt — empty memoryMessages and tools for P04.9
        PromptBuildRequest buildRequest = new PromptBuildRequest(
                request.assembledContext(),
                request.originalQueryText(),
                List.of(),
                List.of(),
                request.tenantId());

        PromptRequest promptRequest = promptBuilderPort.build(buildRequest);

        // 2. Generate with LLM
        LlmRequest  llmRequest  = new LlmRequest(promptRequest, request.options());
        LlmResponse llmResponse = llmPort.generate(llmRequest);

        log.debug("LLM response received: tenant={} model={} finishReason={} tokens={} latencyMs={}",
                request.tenantId(), llmResponse.modelName(),
                llmResponse.finishReason(), llmResponse.totalTokens(), llmResponse.latencyMs());

        // 3. Apply output guardrails
        GuardrailResult guardrailResult = guardrailsPort.apply(llmResponse.generatedText(), request.tenantId());

        if (guardrailResult.isBlocked()) {
            log.warn("Output guardrails blocked LLM response: tenant={}", request.tenantId());
        }

        // 4. Resolve citations — AssembledContext is carried as a read-only passenger
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

    private static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }
}
