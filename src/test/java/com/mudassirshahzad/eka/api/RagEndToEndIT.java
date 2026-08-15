package com.mudassirshahzad.eka.api;

import com.mudassirshahzad.eka.api.security.JwtTokenProvider;
import com.mudassirshahzad.eka.application.generation.GenerationService;
import com.mudassirshahzad.eka.application.retrieval.RetrievalService;
import com.mudassirshahzad.eka.domain.chunk.ChunkId;
import com.mudassirshahzad.eka.domain.conversation.Citation;
import com.mudassirshahzad.eka.domain.generation.model.GeneratedResponse;
import com.mudassirshahzad.eka.domain.retrieval.model.RetrievalResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import com.mudassirshahzad.eka.domain.user.UserRole;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.ChunkEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.DocumentEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.TenantEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.entity.UserEntity;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository.ChunkJpaRepository;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository.DocumentJpaRepository;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository.TenantJpaRepository;
import com.mudassirshahzad.eka.infrastructure.persistence.postgres.repository.UserJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The project's first {@code @SpringBootTest} (P05.1) that boots the complete application
 * context (every controller, service, and adapter wired exactly as in production) and drives a
 * real HTTP request through the real dispatcher servlet, the real
 * {@link com.mudassirshahzad.eka.api.config.SecurityConfig} filter chain — real JWT issuance and
 * validation as of P05.2, not the earlier permissive seam — the real {@code RagOrchestrationService},
 * and real Postgres persistence (Testcontainers).
 *
 * <p>{@link RetrievalService} and {@link GenerationService} are mocked — not because their
 * internals are untested (they have their own dedicated unit test suites), but because
 * exercising them for real here would require live Weaviate and Ollama instances, which would
 * make this test slow, flaky, and environment-dependent for no additional coverage: what this
 * test needs to prove is that P05.1's new wiring (orchestrator, REST layer, persistence) is
 * correct, with the already-proven lower pipeline standing in as a trusted collaborator.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RagEndToEndIT {

    @Autowired private MockMvc          mockMvc;
    @Autowired private ObjectMapper     objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TenantJpaRepository   tenantJpaRepository;
    @Autowired private UserJpaRepository     userJpaRepository;
    @Autowired private DocumentJpaRepository documentJpaRepository;
    @Autowired private ChunkJpaRepository    chunkJpaRepository;

    @MockitoBean private RetrievalService  retrievalService;
    @MockitoBean private GenerationService generationService;

    @Test
    void fullChatTurn_createConversation_sendMessage_persistsBothSidesAndReturnsCitedAnswer() throws Exception {
        TenantEntity tenant = persistTenant();
        UserEntity   user   = persistUser(tenant);
        ChunkEntity  chunk  = persistChunk(tenant, user);
        String       bearerToken = jwtTokenProvider.generateAccessToken(
                UserId.of(user.getId()), TenantId.of(tenant.getId()), Set.of(UserRole.USER));

        Citation citation = Citation.of(ChunkId.of(chunk.getId()), 0.88);
        when(retrievalService.retrieve(any())).thenReturn(
                RetrievalResult.empty("hybrid", 5L, "what is retrieval augmented generation?"));
        when(generationService.generate(any())).thenReturn(new GeneratedResponse(
                "RAG combines retrieval with generation.", List.of(citation), "qwen3", 77, 150L));

        // 1. Create a conversation.
        MvcResult createResult = mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"E2E test chat"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID conversationId = UUID.fromString(created.get("id").asText());

        // 2. Send a message — drives the full RAG orchestration path.
        MvcResult messageResult = mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId)
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"What is retrieval augmented generation?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("RAG combines retrieval with generation."))
                .andExpect(jsonPath("$.citations[0].chunkId").value(citation.chunkId().value().toString()))
                .andExpect(jsonPath("$.modelName").value("qwen3"))
                .andReturn();
        JsonNode answer = objectMapper.readTree(messageResult.getResponse().getContentAsString());
        assertThat(answer.get("totalTokens").asInt()).isEqualTo(77);

        // 3. Fetch the conversation — proves BOTH sides of the turn were actually persisted,
        //    not just returned in the response.
        mockMvc.perform(get("/api/v1/conversations/{id}", conversationId)
                        .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[0].content").value("What is retrieval augmented generation?"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[1].content").value("RAG combines retrieval with generation."))
                .andExpect(jsonPath("$.messages[1].citations[0].chunkId")
                        .value(citation.chunkId().value().toString()));
    }

    @Test
    void unauthenticatedRequest_isRejected_realSecurityConfig() throws Exception {
        // No Authorization header at all — proves P05.2's real JWT-based filter chain is actually
        // active end-to-end (not just unit-tested against the filter chain in isolation), and that
        // ADR O05's temporary permissive seam is genuinely gone, not merely superseded on paper.
        mockMvc.perform(get("/api/v1/conversations/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private TenantEntity persistTenant() {
        TenantEntity tenant = TenantEntity.builder()
                .name("Acme").slug("acme-" + UUID.randomUUID()).active(true).build();
        tenant.setId(UUID.randomUUID());
        return tenantJpaRepository.save(tenant);
    }

    private UserEntity persistUser(TenantEntity tenant) {
        UserEntity user = UserEntity.builder()
                .tenant(tenant).email(UUID.randomUUID() + "@example.com")
                .passwordHash("hash").active(true).build();
        user.setId(UUID.randomUUID());
        return userJpaRepository.save(user);
    }

    /**
     * Citations carry a real FK to {@code chunks} (see {@code citations_chunk_id_fkey}) — by the
     * time P05.1 is in real use, chunks always come from the already-covered ingestion pipeline
     * (P04.13), so a genuine end-to-end proof seeds one rather than fabricating a random UUID.
     */
    private ChunkEntity persistChunk(TenantEntity tenant, UserEntity owner) {
        DocumentEntity document = DocumentEntity.builder()
                .tenant(tenant).owner(owner)
                .filename("policy.pdf").format("PDF").status("INDEXED")
                .build();
        document.setId(UUID.randomUUID());
        documentJpaRepository.save(document);

        ChunkEntity chunk = ChunkEntity.builder()
                .document(document).tenant(tenant)
                .sequenceNumber(0)
                .content("RAG combines retrieval with generation for grounded answers.")
                .chunkingStrategy("SENTENCE_WINDOW")
                .build();
        chunk.setId(UUID.randomUUID());
        return chunkJpaRepository.save(chunk);
    }
}
