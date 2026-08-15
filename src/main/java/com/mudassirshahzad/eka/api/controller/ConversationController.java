package com.mudassirshahzad.eka.api.controller;

import com.mudassirshahzad.eka.api.dto.ConversationDetailResponse;
import com.mudassirshahzad.eka.api.dto.ConversationResponse;
import com.mudassirshahzad.eka.api.dto.CreateConversationRequest;
import com.mudassirshahzad.eka.api.dto.GeneratedAnswerResponse;
import com.mudassirshahzad.eka.api.dto.SendMessageRequest;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationToken;
import com.mudassirshahzad.eka.api.security.RequireRole;
import com.mudassirshahzad.eka.application.conversation.ConversationApplicationService;
import com.mudassirshahzad.eka.application.conversation.CreateConversationCommand;
import com.mudassirshahzad.eka.application.orchestration.RagOrchestrationService;
import com.mudassirshahzad.eka.application.orchestration.RagTurnResult;
import com.mudassirshahzad.eka.application.orchestration.SendMessageCommand;
import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * First REST surface for Project EKA (P05.1). Deliberately thin: every method validates,
 * converts DTOs, invokes the application layer, and converts the response — no business logic
 * lives here.
 *
 * <p>{@code tenantId}/{@code userId} are read from the authenticated {@link JwtAuthenticationToken}
 * Spring Security populates from the validated Bearer token (P05.2, ADR A05) — every method here
 * is reachable only when {@code SecurityConfig} has already required authentication, so the cast
 * from {@link Authentication} is safe: {@code JwtAuthenticationFilter} is the only component that
 * ever places an {@code Authentication} in the security context.
 *
 * <p>{@link RequireRole} declares which roles may call the two mutating endpoints (ADR AZ02) —
 * {@code getConversation} carries none, since read access is open to every authenticated role and
 * is instead gated by ownership, enforced inside {@code ConversationApplicationService} (ADR TN01),
 * never here.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationApplicationService conversationApplicationService;
    private final RagOrchestrationService         ragOrchestrationService;

    @PostMapping
    @RequireRole({UserRole.USER, UserRole.ADMIN})
    public ResponseEntity<ConversationResponse> createConversation(
            Authentication authentication,
            @Valid @RequestBody CreateConversationRequest request) {

        JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication;
        Conversation conversation = conversationApplicationService.createConversation(
                new CreateConversationCommand(principal.userId(), principal.tenantId(), request.title()));

        ConversationResponse response = ConversationResponse.from(conversation);
        return ResponseEntity.created(URI.create("/api/v1/conversations/" + response.id())).body(response);
    }

    @GetMapping("/{conversationId}")
    public ConversationDetailResponse getConversation(
            Authentication authentication,
            @PathVariable UUID conversationId) {

        JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication;
        Conversation conversation = conversationApplicationService.getConversation(
                ConversationId.of(conversationId), principal.userId(), principal.tenantId());

        return ConversationDetailResponse.from(conversation);
    }

    @PostMapping("/{conversationId}/messages")
    @RequireRole({UserRole.USER, UserRole.ADMIN})
    public GeneratedAnswerResponse sendMessage(
            Authentication authentication,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {

        JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication;
        RagTurnResult result = ragOrchestrationService.handleUserMessage(new SendMessageCommand(
                ConversationId.of(conversationId), principal.userId(), principal.tenantId(), request.content()));

        return GeneratedAnswerResponse.from(result);
    }
}
