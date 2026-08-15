package com.mudassirshahzad.eka.api.controller;

import com.mudassirshahzad.eka.api.config.SecurityConfig;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationFilter;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationToken;
import com.mudassirshahzad.eka.api.security.JwtTokenProvider;
import com.mudassirshahzad.eka.api.security.RestAuthenticationEntryPoint;
import com.mudassirshahzad.eka.application.conversation.ConversationApplicationService;
import com.mudassirshahzad.eka.application.generation.GenerationException;
import com.mudassirshahzad.eka.application.orchestration.RagOrchestrationService;
import com.mudassirshahzad.eka.application.orchestration.RagTurnResult;
import com.mudassirshahzad.eka.application.shared.DuplicateResourceException;
import com.mudassirshahzad.eka.application.shared.ResourceNotFoundException;
import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.conversation.Citation;
import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.generation.model.GeneratedResponse;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice covering the controller, its DTO (de)serialization, and its
 * integration with {@link GlobalExceptionHandler} (ADR O03) and the real, JWT-based
 * {@link SecurityConfig} (P05.2). {@code tenantId}/{@code userId} now come from the authenticated
 * {@link JwtAuthenticationToken} (ADR A05) — attached per-request via
 * {@code SecurityMockMvcRequestPostProcessors.authentication(...)} rather than request-body
 * fields, since those fields no longer exist on the DTOs.
 */
@WebMvcTest(ConversationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class ConversationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ConversationApplicationService conversationApplicationService;
    @MockitoBean private RagOrchestrationService         ragOrchestrationService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    private final UUID userId   = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    private RequestPostProcessor authenticated() {
        return authentication(new JwtAuthenticationToken(
                UserId.of(userId), TenantId.of(tenantId), List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    // ── POST /conversations ───────────────────────────────────────────────────

    @Test
    void createConversation_returnsCreatedWithLocationAndBody() throws Exception {
        Conversation conversation = Conversation.create(UserId.of(userId), TenantId.of(tenantId), "My chat");
        when(conversationApplicationService.createConversation(any())).thenReturn(conversation);

        mockMvc.perform(post("/api/v1/conversations")
                        .with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"My chat"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/conversations/" + conversation.getId().value()))
                .andExpect(jsonPath("$.id").value(conversation.getId().value().toString()))
                .andExpect(jsonPath("$.title").value("My chat"));
    }

    @Test
    void createConversation_blankTitle_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/conversations")
                        .with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createConversation_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"My chat"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createConversation_duplicateResource_returnsConflict() throws Exception {
        when(conversationApplicationService.createConversation(any()))
                .thenThrow(new DuplicateResourceException("Conversation already exists"));

        mockMvc.perform(post("/api/v1/conversations")
                        .with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"chat"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ── GET /conversations/{id} ────────────────────────────────────────────────

    @Test
    void getConversation_returnsConversationDetail() throws Exception {
        Conversation conversation = Conversation.create(UserId.of(userId), TenantId.of(tenantId), "chat");
        conversation.addMessage(Message.userMessage("hi"));
        when(conversationApplicationService.getConversation(any(), any())).thenReturn(conversation);

        mockMvc.perform(get("/api/v1/conversations/{id}", conversation.getId().value())
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].content").value("hi"));
    }

    @Test
    void getConversation_notFound_returnsProblemDetail() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(conversationApplicationService.getConversation(any(), any()))
                .thenThrow(new ResourceNotFoundException("Conversation", missingId.toString()));

        mockMvc.perform(get("/api/v1/conversations/{id}", missingId)
                        .with(authenticated()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("Conversation")));
    }

    @Test
    void getConversation_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /conversations/{id}/messages ─────────────────────────────────────

    @Test
    void sendMessage_returnsGeneratedAnswerWithCitations() throws Exception {
        ConversationId conversationId    = ConversationId.generate();
        Citation       citation          = Citation.of(ChunkId.generate(), 0.9);
        Message        assistantMessage  = Message.assistantMessage("the answer", List.of(citation), null);
        GeneratedResponse generated      = new GeneratedResponse("the answer", List.of(citation), "qwen3", 50, 120L);
        when(ragOrchestrationService.handleUserMessage(any()))
                .thenReturn(new RagTurnResult(assistantMessage, generated));

        mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId.value())
                        .with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"What is RAG?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("the answer"))
                .andExpect(jsonPath("$.citations[0].chunkId").value(citation.chunkId().value().toString()))
                .andExpect(jsonPath("$.modelName").value("qwen3"))
                .andExpect(jsonPath("$.totalTokens").value(50));
    }

    @Test
    void sendMessage_blankContent_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{id}/messages", UUID.randomUUID())
                        .with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessage_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{id}/messages", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"question"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendMessage_upstreamGenerationFailure_returnsBadGateway() throws Exception {
        when(ragOrchestrationService.handleUserMessage(any()))
                .thenThrow(new GenerationException("LLM unavailable"));

        mockMvc.perform(post("/api/v1/conversations/{id}/messages", UUID.randomUUID())
                        .with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"question"}
                                """))
                .andExpect(status().isBadGateway());
    }

    @Test
    void sendMessage_unexpectedFailure_returnsInternalServerErrorWithoutLeakingDetail() throws Exception {
        when(ragOrchestrationService.handleUserMessage(any()))
                .thenThrow(new RuntimeException("sensitive internal detail"));

        mockMvc.perform(post("/api/v1/conversations/{id}/messages", UUID.randomUUID())
                        .with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"question"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }
}
