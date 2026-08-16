package com.mudassirshahzad.eka.application.orchestration;

import com.mudassirshahzad.eka.application.conversation.AddAssistantMessageCommand;
import com.mudassirshahzad.eka.application.conversation.AddUserMessageCommand;
import com.mudassirshahzad.eka.application.conversation.ConversationApplicationService;
import com.mudassirshahzad.eka.application.generation.GenerationRequest;
import com.mudassirshahzad.eka.application.generation.GenerationService;
import com.mudassirshahzad.eka.application.retrieval.RetrievalRequest;
import com.mudassirshahzad.eka.application.retrieval.RetrievalService;
import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.generation.model.GeneratedResponse;
import com.mudassirshahzad.eka.domain.generation.model.GenerationOptions;
import com.mudassirshahzad.eka.domain.query.MetadataFilter;
import com.mudassirshahzad.eka.domain.retrieval.model.AssembledContext;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievalOptions;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievalResult;
import com.mudassirshahzad.eka.domain.retrieval.port.ContextAssemblyPort;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Coordinates one full RAG turn end to end (P05.1): persist the user's message, retrieve
 * context, generate a guardrail-checked and cited answer, persist that answer.
 *
 * <p>This service intentionally contains no business logic of its own — every step is a single
 * call into an existing service, each justified by one sentence (ADR O01):
 * <ol>
 *   <li>{@link ConversationApplicationService#addUserMessage} — persist what the user said</li>
 *   <li>{@link RetrievalService#retrieve} — find relevant chunks</li>
 *   <li>{@link ContextAssemblyPort#assemble} — turn chunks into a token-budgeted context</li>
 *   <li>{@link GenerationService#generate} — produce a cited, guardrail-checked answer</li>
 *   <li>{@link ConversationApplicationService#addAssistantMessage} — persist what came back</li>
 * </ol>
 * If a future step needs more than one sentence to justify, that logic belongs in the service
 * being called, not here.
 *
 * <p>Neither {@link GenerationService} nor {@link RetrievalService} nor
 * {@link ConversationApplicationService} were modified to support this — this class is purely
 * additive composition over existing, unchanged application services.
 */
@Slf4j
@Service
public class RagOrchestrationService {

    private final ConversationApplicationService conversationApplicationService;
    private final RetrievalService                retrievalService;
    private final ContextAssemblyPort              contextAssemblyPort;
    private final GenerationService                generationService;
    private final ObservationRegistry              observationRegistry;
    private final int                              contextTokenBudget;

    public RagOrchestrationService(
            ConversationApplicationService conversationApplicationService,
            RetrievalService                retrievalService,
            ContextAssemblyPort             contextAssemblyPort,
            GenerationService                generationService,
            ObservationRegistry             observationRegistry,
            @Value("${app.context.token-budget:4096}") int contextTokenBudget) {
        this.conversationApplicationService = Objects.requireNonNull(
                conversationApplicationService, "conversationApplicationService must not be null");
        this.retrievalService = Objects.requireNonNull(
                retrievalService, "retrievalService must not be null");
        this.contextAssemblyPort = Objects.requireNonNull(
                contextAssemblyPort, "contextAssemblyPort must not be null");
        this.generationService = Objects.requireNonNull(
                generationService, "generationService must not be null");
        this.observationRegistry = Objects.requireNonNull(
                observationRegistry, "observationRegistry must not be null");
        if (contextTokenBudget < 1) {
            throw new IllegalArgumentException(
                    "contextTokenBudget must be >= 1 but was " + contextTokenBudget);
        }
        this.contextTokenBudget = contextTokenBudget;
    }

    /**
     * Runs the whole turn inside an {@code eka.orchestration} {@link Observation} (P05.4, ADR
     * OB02) — a Micrometer timer spanning persistence + retrieval + generation together, trace-ready
     * with zero code changes once distributed tracing is added.
     */
    public RagTurnResult handleUserMessage(SendMessageCommand cmd) {
        Objects.requireNonNull(cmd, "cmd must not be null");
        return Observation.createNotStarted("eka.orchestration", observationRegistry)
                .observe(() -> doHandleUserMessage(cmd));
    }

    private RagTurnResult doHandleUserMessage(SendMessageCommand cmd) {
        log.debug("RAG turn started: tenant={} conversation={}", cmd.tenantId(), cmd.conversationId());

        conversationApplicationService.addUserMessage(
                new AddUserMessageCommand(cmd.conversationId(), cmd.userId(), cmd.tenantId(), cmd.content()));

        RetrievalResult retrievalResult = retrievalService.retrieve(new RetrievalRequest(
                cmd.content(), cmd.tenantId(), cmd.userId(), MetadataFilter.NONE, RetrievalOptions.DEFAULT));

        AssembledContext assembledContext = contextAssemblyPort.assemble(
                retrievalResult.items(), retrievalResult.effectiveQueryText(), contextTokenBudget);

        GeneratedResponse generatedResponse = generationService.generate(new GenerationRequest(
                assembledContext, cmd.content(), cmd.tenantId(), GenerationOptions.DEFAULT, cmd.conversationId()));

        Conversation updated = conversationApplicationService.addAssistantMessage(new AddAssistantMessageCommand(
                cmd.conversationId(), cmd.userId(), cmd.tenantId(),
                generatedResponse.generatedText(), generatedResponse.citations()));

        Message assistantMessage = updated.getMessages().getLast();

        log.info("RAG turn complete: tenant={} conversation={} citations={} totalTokens={} latencyMs={}",
                cmd.tenantId(), cmd.conversationId(), generatedResponse.citations().size(),
                generatedResponse.totalTokens(), generatedResponse.latencyMs());

        return new RagTurnResult(assistantMessage, generatedResponse);
    }
}
