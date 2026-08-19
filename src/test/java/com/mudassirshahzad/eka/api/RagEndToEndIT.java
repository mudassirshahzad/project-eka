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
import io.micrometer.core.instrument.MeterRegistry;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 *
 * <p>As of P05.3, this is also the only place the full chain — JWT → authentication →
 * role authorization ({@code AuthorizationInterceptor}) → application (tenant/ownership check,
 * ADR TN01) → real Postgres persistence → response — is exercised together against a real
 * database, rather than against mocked collaborators.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RagEndToEndIT {

    @Autowired private MockMvc          mockMvc;
    @Autowired private ObjectMapper     objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MeterRegistry    meterRegistry;
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

        // P05.4: proves the Observation-based orchestration timer (ADR OB02) actually recorded
        // this real request — not just that the code compiles — and that Spring Boot's own
        // auto-instrumented http.server.requests metric is active, so "request counts/response
        // times" needs no custom counter of its own.
        assertThat(meterRegistry.find("eka.orchestration").timer()).isNotNull();
        assertThat(meterRegistry.find("eka.orchestration").timer().count()).isGreaterThanOrEqualTo(1);
        assertThat(meterRegistry.find("http.server.requests").timers()).isNotEmpty();
    }

    @Test
    void response_carriesCorrelationIdHeader() throws Exception {
        // P05.4, ADR OB03: every response — including one from a permitAll, unauthenticated
        // endpoint — carries a correlation ID, proving CorrelationIdFilter runs before Spring
        // Security's own filters, not just before JwtAuthenticationFilter.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void actuatorHealth_isPubliclyReachable_andReportsUp() throws Exception {
        // DB is the only health contributor active in the test profile (ollama/weaviate disabled —
        // see application.yml's test profile block, since no live instances run in tests).
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void actuatorInfo_isPubliclyReachable() throws Exception {
        // build.version comes from build-info.properties (springBoot { buildInfo() },
        // v0.6.1 ADR EX03) — generated from build.gradle's `version` at build time, so this
        // assertion also proves the single-source-of-truth wiring actually works, not just that
        // the endpoint is reachable.
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("Project EKA"))
                .andExpect(jsonPath("$.build.version").exists());
    }

    @Test
    void unauthenticatedRequest_isRejected_realSecurityConfig() throws Exception {
        // No Authorization header at all — proves P05.2's real JWT-based filter chain is actually
        // active end-to-end (not just unit-tested against the filter chain in isolation), and that
        // ADR O05's temporary permissive seam is genuinely gone, not merely superseded on paper.
        mockMvc.perform(get("/api/v1/conversations/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crossTenantAccess_returnsNotFound_realDatabaseProof() throws Exception {
        // Tenant A creates a conversation; Tenant B (a completely different, real, persisted
        // tenant/user pair) holds a validly-signed token and tries to read it. Proves ADR TN01's
        // tenant check against a real ConversationRepository/Postgres round trip, not a mock.
        TenantEntity tenantA = persistTenant();
        UserEntity   userA   = persistUser(tenantA);
        String       tokenA  = jwtTokenProvider.generateAccessToken(
                UserId.of(userA.getId()), TenantId.of(tenantA.getId()), Set.of(UserRole.USER));

        MvcResult createResult = mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Tenant A's private chat"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID conversationId = UUID.fromString(
                objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText());

        TenantEntity tenantB = persistTenant();
        UserEntity   userB   = persistUser(tenantB);
        String       tokenB  = jwtTokenProvider.generateAccessToken(
                UserId.of(userB.getId()), TenantId.of(tenantB.getId()), Set.of(UserRole.USER));

        mockMvc.perform(get("/api/v1/conversations/{id}", conversationId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUserInSameTenant_cannotReadConversation_realDatabaseProof() throws Exception {
        // Distinct from the cross-tenant case above: same tenant, different owning user — proves
        // ownership scoping (findByIdAndUserId), not the tenant check, is what's rejecting this.
        TenantEntity tenant = persistTenant();
        UserEntity   owner  = persistUser(tenant);
        UserEntity   other  = persistUser(tenant);
        String       ownerToken = jwtTokenProvider.generateAccessToken(
                UserId.of(owner.getId()), TenantId.of(tenant.getId()), Set.of(UserRole.USER));
        String       otherToken = jwtTokenProvider.generateAccessToken(
                UserId.of(other.getId()), TenantId.of(tenant.getId()), Set.of(UserRole.USER));

        MvcResult createResult = mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"owner's chat"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID conversationId = UUID.fromString(
                objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(get("/api/v1/conversations/{id}", conversationId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewerRole_cannotCreateConversation_realFilterChain() throws Exception {
        // The role check happens purely off the token's embedded claims (ADR AZ02) — no
        // persisted user is needed to prove AuthorizationInterceptor rejects this before the
        // controller (and therefore the database) is ever reached.
        String viewerToken = jwtTokenProvider.generateAccessToken(
                UserId.generate(), TenantId.generate(), Set.of(UserRole.VIEWER));

        mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"should be rejected"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void bootstrapThenLogin_realPasswordHashRoundTrip() throws Exception {
        // P06.1: proves the real chain, not a mock — bootstrap's PasswordEncoder.encode(...)
        // output must be verifiable by AuthenticateUserUseCase's real PasswordEncoder.matches(...)
        // on a genuinely separate request, against a real Postgres-persisted row.
        TenantEntity tenant = persistTenant();
        String email = "admin-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/v1/admin/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","email":"%s","password":"bootstrap123"}
                                """.formatted(tenant.getId(), email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","email":"%s","password":"bootstrap123"}
                                """.formatted(tenant.getId(), email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void bootstrap_secondCallOnSameTenant_returnsBadRequest_realDatabaseProof() throws Exception {
        // Proves the "only once per tenant" guard against a real existsByTenantId query, not a
        // mocked repository — the first call genuinely persists a row the second call must see.
        TenantEntity tenant = persistTenant();

        mockMvc.perform(post("/api/v1/admin/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","email":"first-admin@example.com","password":"bootstrap123"}
                                """.formatted(tenant.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"%s","email":"second-admin@example.com","password":"bootstrap123"}
                                """.formatted(tenant.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDocument_crossTenantAccess_returnsNotFound_realDatabaseProof() throws Exception {
        // Mirrors the conversation cross-tenant proof above, applied to the new document
        // endpoint — DocumentApplicationService.getDocument is tenant-scoped, not owner-scoped
        // (documents are shared tenant knowledge-base content), so this proves the tenant
        // boundary specifically, against a real DocumentRepository/Postgres round trip.
        TenantEntity tenantA   = persistTenant();
        UserEntity   ownerA    = persistUser(tenantA);
        DocumentEntity document = persistDocument(tenantA, ownerA);

        TenantEntity tenantB = persistTenant();
        UserEntity   userB   = persistUser(tenantB);
        String       tokenB  = jwtTokenProvider.generateAccessToken(
                UserId.of(userB.getId()), TenantId.of(tenantB.getId()), Set.of(UserRole.USER));

        mockMvc.perform(get("/api/v1/documents/{id}", document.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDocument_sameTenant_anyRole_isReadable_realDatabaseProof() throws Exception {
        // Confirms the deliberate design (DocumentController Javadoc, P06.1): documents are
        // tenant-wide readable, not owner-scoped like conversations — any authenticated role in
        // the same tenant can read another user's uploaded document.
        TenantEntity tenant = persistTenant();
        UserEntity   owner  = persistUser(tenant);
        UserEntity   reader = persistUser(tenant);
        DocumentEntity document = persistDocument(tenant, owner);
        String readerToken = jwtTokenProvider.generateAccessToken(
                UserId.of(reader.getId()), TenantId.of(tenant.getId()), Set.of(UserRole.VIEWER));

        mockMvc.perform(get("/api/v1/documents/{id}", document.getId())
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("policy.pdf"));
    }

    @Test
    void deleteConversation_thenGet_returnsNotFound_realDatabaseProof() throws Exception {
        // Proves the new DELETE route's soft-delete actually reaches Postgres — a subsequent GET
        // through the real findByIdAndUserId query no longer finds it, not just that DELETE
        // itself returned 204.
        TenantEntity tenant = persistTenant();
        UserEntity   user   = persistUser(tenant);
        String       token  = jwtTokenProvider.generateAccessToken(
                UserId.of(user.getId()), TenantId.of(tenant.getId()), Set.of(UserRole.USER));

        MvcResult createResult = mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"to be deleted"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID conversationId = UUID.fromString(
                objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(delete("/api/v1/conversations/{id}", conversationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/conversations/{id}", conversationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listConversations_returnsOnlyCallersOwnConversations_realDatabaseProof() throws Exception {
        TenantEntity tenant = persistTenant();
        UserEntity   userA  = persistUser(tenant);
        UserEntity   userB  = persistUser(tenant);
        String       tokenA = jwtTokenProvider.generateAccessToken(
                UserId.of(userA.getId()), TenantId.of(tenant.getId()), Set.of(UserRole.USER));
        String       tokenB = jwtTokenProvider.generateAccessToken(
                UserId.of(userB.getId()), TenantId.of(tenant.getId()), Set.of(UserRole.USER));

        mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"A's chat"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/conversations")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(0)));

        mockMvc.perform(get("/api/v1/conversations")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("A's chat"));
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

    private DocumentEntity persistDocument(TenantEntity tenant, UserEntity owner) {
        DocumentEntity document = DocumentEntity.builder()
                .tenant(tenant).owner(owner)
                .filename("policy.pdf").format("PDF").status("INDEXED")
                .build();
        document.setId(UUID.randomUUID());
        return documentJpaRepository.save(document);
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
