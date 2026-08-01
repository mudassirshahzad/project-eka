package com.mudassirshahzad.eka.api.controller;

import com.mudassirshahzad.eka.api.dto.ConversationDetailResponse;
import com.mudassirshahzad.eka.api.dto.ConversationResponse;
import com.mudassirshahzad.eka.api.dto.CreateConversationRequest;
import com.mudassirshahzad.eka.api.dto.GeneratedAnswerResponse;
import com.mudassirshahzad.eka.api.dto.SendMessageRequest;
import com.mudassirshahzad.eka.application.conversation.ConversationApplicationService;
import com.mudassirshahzad.eka.application.conversation.CreateConversationCommand;
import com.mudassirshahzad.eka.application.orchestration.RagOrchestrationService;
import com.mudassirshahzad.eka.application.orchestration.RagTurnResult;
import com.mudassirshahzad.eka.application.orchestration.SendMessageCommand;
import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * First REST surface for Project EKA (P05.1). Deliberately thin: every method validates,
 * converts DTOs, invokes the application layer, and converts the response — no business logic
 * lives here.
 *
 * <p>{@code tenantId}/{@code userId} are read from request bodies/params rather than an
 * authenticated principal, because there is no authentication yet (ADR O05) — this is a known,
 * temporary characteristic of P05.1, replaced in P05.2.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationApplicationService conversationApplicationService;
    private final RagOrchestrationService         ragOrchestrationService;

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @Valid @RequestBody CreateConversationRequest request) {

        Conversation conversation = conversationApplicationService.createConversation(
                new CreateConversationCommand(
                        UserId.of(request.userId()),
                        TenantId.of(request.tenantId()),
                        request.title()));

        ConversationResponse response = ConversationResponse.from(conversation);
        return ResponseEntity.created(URI.create("/api/v1/conversations/" + response.id())).body(response);
    }

    @GetMapping("/{conversationId}")
    public ConversationDetailResponse getConversation(
            @PathVariable UUID conversationId,
            @RequestParam UUID userId) {

        Conversation conversation = conversationApplicationService.getConversation(
                ConversationId.of(conversationId), UserId.of(userId));

        return ConversationDetailResponse.from(conversation);
    }

    @PostMapping("/{conversationId}/messages")
    public GeneratedAnswerResponse sendMessage(
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {

        RagTurnResult result = ragOrchestrationService.handleUserMessage(new SendMessageCommand(
                ConversationId.of(conversationId),
                UserId.of(request.userId()),
                TenantId.of(request.tenantId()),
                request.content()));

        return GeneratedAnswerResponse.from(result);
    }
}
