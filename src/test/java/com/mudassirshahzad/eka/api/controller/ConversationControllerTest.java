package com.mudassirshahzad.eka.api.controller;

import com.mudassirshahzad.eka.api.config.SecurityConfig;
import com.mudassirshahzad.eka.api.config.WebMvcConfig;
import com.mudassirshahzad.eka.api.observability.CorrelationIdFilter;
import com.mudassirshahzad.eka.api.security.AuthorizationInterceptor;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationFilter;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationToken;
import com.mudassirshahzad.eka.api.security.JwtTokenProvider;
import com.mudassirshahzad.eka.api.security.RequestSizeLimitFilter;
import com.mudassirshahzad.eka.api.security.RestAuthenticationEntryPoint;
import com.mudassirshahzad.eka.application.conversation.ConversationApplicationService;
import com.mudassirshahzad.eka.application.conversation.CreateConversationUseCase;
import com.mudassirshahzad.eka.application.conversation.DeleteConversationUseCase;
import com.mudassirshahzad.eka.application.generation.GenerationException;
import com.mudassirshahzad.eka.application.orchestration.RagOrchestrationService;
import com.mudassirshahzad.eka.application.orchestration.RagTurnResult;
import com.mudassirshahzad.eka.application.shared.ApplicationException;
import com.mudassirshahzad.eka.application.shared.DuplicateResourceException;
import com.mudassirshahzad.eka.application.shared.ResourceNotFoundException;
import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.conversation.Citation;
import com.mudassirshahzad.eka.domain.conversation.Conversation;
import com.mudassirshahzad.eka.domain.conversation.ConversationId;
import com.mudassirshahzad.eka.domain.conversation.Message;
import com.mudassirshahzad.eka.domain.generation.model.GeneratedResponse;
import com.mudassirshahzad.eka.domain.shared.PageRequest;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice covering the controller, its DTO (de)serialization, and its
 * integration with {@link GlobalExceptionHandler} (ADR O03), the real JWT-based
 * {@link SecurityConfig} (P05.2), and the real role-authorization boundary — {@link WebMvcConfig}
 * plus {@link AuthorizationInterceptor} (P05.3, ADR AZ01/AZ02). {@code tenantId}/{@code userId}
 * come from the authenticated {@link JwtAuthenticationToken} (ADR A05) — attached per-request via
 * {@code SecurityMockMvcRequestPostProcessors.authentication(...)} rather than request-body
 * fields, since those fields no longer exist on the DTOs. A real {@link SimpleMeterRegistry}
 * (P05.4) is provided via {@link MeterRegistryTestConfig} — {@code JwtAuthenticationFilter} and
 * {@code AuthorizationInterceptor} both now record failure counters and need a real registry, not
 * a mock, since a mocked {@code counter(...)} call would return {@code null}.
 */
@WebMvcTest(ConversationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        WebMvcConfig.class, AuthorizationInterceptor.class, CorrelationIdFilter.class,
        RequestSizeLimitFilter.class, ConversationControllerTest.MeterRegistryTestConfig.class})
class ConversationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ConversationApplicationService conversationApplicationService;
    @MockitoBean private CreateConversationUseCase       createConversationUseCase;
    @MockitoBean private DeleteConversationUseCase       deleteConversationUseCase;
    @MockitoBean private RagOrchestrationService         ragOrchestrationService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @TestConfiguration
    static class MeterRegistryTestConfig {
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private final UUID userId   = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    private RequestPostProcessor authenticated() {
        return authenticatedAs("ROLE_USER");
    }

    private RequestPostProcessor authenticatedAs(String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toList();
        return authentication(new JwtAuthenticationToken(UserId.of(userId), TenantId.of(tenantId), granted));
    }

    // ── POST /conversations ───────────────────────────────────────────────────

    @Test
    void createConversation_returnsCreatedWithLocationAndBody() throws Exception {
        Conversation conversation = Conversation.create(UserId.of(userId), TenantId.of(tenantId), "My chat");
        when(createConversationUseCase.execute(any())).thenReturn(conversation);

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
    void createConversation_viewerRole_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/conversations")
                        .with(authenticatedAs("ROLE_VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"My chat"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void createConversation_adminRole_isPermitted() throws Exception {
        Conversation conversation = Conversation.create(UserId.of(userId), TenantId.of(tenantId), "My chat");
        when(createConversationUseCase.execute(any())).thenReturn(conversation);

        mockMvc.perform(post("/api/v1/conversations")
                        .with(authenticatedAs("ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"My chat"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void createConversation_duplicateResource_returnsConflict() throws Exception {
        when(createConversationUseCase.execute(any()))
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
        when(conversationApplicationService.getConversation(any(), any(), any())).thenReturn(conversation);

        mockMvc.perform(get("/api/v1/conversations/{id}", conversation.getId().value())
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].content").value("hi"));
    }

    @Test
    void getConversation_notFound_returnsProblemDetail() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(conversationApplicationService.getConversation(any(), any(), any()))
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

    @Test
    void getConversation_viewerRole_isPermitted() throws Exception {
        Conversation conversation = Conversation.create(UserId.of(userId), TenantId.of(tenantId), "chat");
        when(conversationApplicationService.getConversation(any(), any(), any())).thenReturn(conversation);

        mockMvc.perform(get("/api/v1/conversations/{id}", conversation.getId().value())
                        .with(authenticatedAs("ROLE_VIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    void getConversation_belongsToAnotherTenant_returnsNotFoundNotForbidden() throws Exception {
        // Ownership/tenant mismatch is enforced by ConversationApplicationService (ADR TN01) as a
        // 404, not a 403 — this proves the controller doesn't re-decide or mask that outcome.
        UUID otherTenantsConversationId = UUID.randomUUID();
        when(conversationApplicationService.getConversation(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Conversation", otherTenantsConversationId.toString()));

        mockMvc.perform(get("/api/v1/conversations/{id}", otherTenantsConversationId)
                        .with(authenticated()))
                .andExpect(status().isNotFound());
    }

    /**
     * v0.6.1, ADR EX02: before {@code GlobalExceptionHandler} extended
     * {@code ResponseEntityExceptionHandler}, a non-UUID path segment threw
     * {@code MethodArgumentTypeMismatchException}, which fell through to the generic
     * {@code Exception.class} handler and returned 500 — a client mistake misreported as a server
     * failure. This proves it now resolves to 400 with a {@code ProblemDetail} body, never
     * reaching the application layer at all.
     */
    @Test
    void getConversation_nonUuidPathVariable_returnsBadRequestNotServerError() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/{id}", "not-a-uuid")
                        .with(authenticated()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ── GET /conversations (P06.1) ──────────────────────────────────────────────

    @Test
    void listConversations_returnsPageOfOwnConversations() throws Exception {
        Conversation conversation = Conversation.create(UserId.of(userId), TenantId.of(tenantId), "My chat");
        PageResult<Conversation> page = PageResult.of(List.of(conversation), 0, 20, 1);
        when(conversationApplicationService.listConversations(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/conversations")
                        .with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("My chat"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listConversations_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listConversations_viewerRole_isPermitted() throws Exception {
        PageResult<Conversation> page = PageResult.of(List.of(), 0, 20, 0);
        when(conversationApplicationService.listConversations(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/conversations")
                        .with(authenticatedAs("ROLE_VIEWER")))
                .andExpect(status().isOk());
    }

    // ── DELETE /conversations/{id} (P06.1) ──────────────────────────────────────

    @Test
    void deleteConversation_returnsNoContent() throws Exception {
        UUID conversationId = UUID.randomUUID();
        doNothing().when(deleteConversationUseCase).execute(any(), any(), any());

        mockMvc.perform(delete("/api/v1/conversations/{id}", conversationId)
                        .with(authenticated()))
                .andExpect(status().isNoContent());

        verify(deleteConversationUseCase).execute(ConversationId.of(conversationId), UserId.of(userId), TenantId.of(tenantId));
    }

    @Test
    void deleteConversation_notFound_returnsProblemDetail() throws Exception {
        UUID missingId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Conversation", missingId.toString()))
                .when(deleteConversationUseCase).execute(any(), any(), any());

        mockMvc.perform(delete("/api/v1/conversations/{id}", missingId)
                        .with(authenticated()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteConversation_activeSessionGuard_returnsBadRequest() throws Exception {
        // P06.1, ADR PC04: a bare ApplicationException (here, DeleteConversationUseCase's
        // active-chat-session guard) now maps to 400, not 500.
        doThrow(new ApplicationException("Cannot delete conversation with an active session"))
                .when(deleteConversationUseCase).execute(any(), any(), any());

        mockMvc.perform(delete("/api/v1/conversations/{id}", UUID.randomUUID())
                        .with(authenticated()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("active session")));
    }

    @Test
    void deleteConversation_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/conversations/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteConversation_viewerRole_returnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/conversations/{id}", UUID.randomUUID())
                        .with(authenticatedAs("ROLE_VIEWER")))
                .andExpect(status().isForbidden());
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

    /**
     * v0.6.1, ADR EX02: malformed JSON threw {@code HttpMessageNotReadableException}, previously
     * misreported as 500 by the generic {@code Exception.class} handler.
     */
    @Test
    void sendMessage_malformedJsonBody_returnsBadRequestNotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{id}/messages", UUID.randomUUID())
                        .with(authenticated())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void sendMessage_auditorRole_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{id}/messages", UUID.randomUUID())
                        .with(authenticatedAs("ROLE_AUDITOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"question"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
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
