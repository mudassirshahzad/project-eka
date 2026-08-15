# Project State

Current Version

v0.5.3 (In Progress) — Phase 5: Application Platform

**Namespace:** Root package is `com.mudassirshahzad.eka` (renamed from `com.mudassir.eka` in R01 — pure namespace refactor, no behavioral or architectural change).

---

## Phase 4 — Foundation (Complete)

| Milestone | Description                        | New Tests | Status     |
|-----------|------------------------------------|-----------|------------|
| P04.1     | Retrieval Foundation               | —         | ✅ Complete |
| P04.2     | Vector Retrieval                   | —         | ✅ Complete |
| P04.3     | PostgreSQL BM25 Retrieval          | —         | ✅ Complete |
| P04.4     | Reciprocal Rank Fusion             | —         | ✅ Complete |
| P04.AC1   | Single Embedding Pipeline          | +10       | ✅ Complete |
| P04.5     | Hybrid Retrieval                   | +16       | ✅ Complete |
| P04.6     | Query Rewriting                    | +21       | ✅ Complete |
| P04.7     | Context Assembly                   | +28       | ✅ Complete |
| P04.8     | Prompt Builder                     | +50       | ✅ Complete |
| P04.9     | Chat Generation (LLM + Guardrails) | +82       | ✅ Complete |
| P04.10    | Conversation Memory                | +23       | ✅ Complete |
| P04.11    | Citation Engine                    | +20       | ✅ Complete |
| P04.12    | Enterprise Output Guardrails       | +14       | ✅ Complete |
| P04.13    | Architecture Reconciliation        | +19       | ✅ Complete |

**Phase 4 total tests: 489 — 0 failures**

---

## Phase 5 — Application Platform

| Milestone | Description                                     | New Tests | Status     |
|-----------|--------------------------------------------------|-----------|------------|
| P05.1     | End-to-End RAG Orchestration & REST Exposure     | +41       | ✅ Complete |
| P05.2     | Authentication Foundation                       | +21       | ✅ Complete |
| P05.3     | Tenant & Role Authorization Boundary            | +22       | ✅ Complete |

**Grand total tests: 573 — 0 failures**

---

## Current Milestone

**P05.4 — Observability Foundation** ← next to implement

---

## Architecture Notes

Architecture is frozen.

Security layer (Authorization Filter) is planned but not implemented.

### Frozen ADRs

| ADR  | Decision                                                                                          |
|------|---------------------------------------------------------------------------------------------------|
| G01  | `PromptBuilderPort` returns `PromptRequest` (provider-independent); LLM adapter converts         |
| G02  | `PromptRequest.userText` is the original verbatim query, never the rewritten retrieval form       |
| G03  | Source markers are 1-based: `[SOURCE:N]` where N = chunk.position() + 1                          |
| G04  | `TemplateBasedPromptBuilderAdapter` is stateless — same inputs always produce same output         |
| G05  | `PromptBuilderPort` signature frozen; future params travel inside `PromptBuildRequest`            |
| G06  | System prompt template at `classpath:prompts/qa-system.txt`; missing = fail fast at startup      |
| G07  | `ToolDefinition` is a stub passthrough for P04.8/P04.9; no execution occurs                      |
| G08  | `PromptBuildRequest` is the single frozen input to `PromptBuilderPort`                            |
| G09  | `LlmPort.generate()` takes `LlmRequest` (wraps `PromptRequest` + `GenerationOptions`)            |
| G10  | `LlmResponse` fields: generatedText, finishReason, modelName, promptTokens, completionTokens, latencyMs |
| G11  | `LlmPort` is synchronous; streaming deferred to future `StreamingLlmPort`                         |
| G12  | `LlmException` base in domain; infrastructure subtypes in `infrastructure.llm.exception`         |
| G13  | Retry logic belongs inside the provider adapter, not `GenerationService`                          |
| G14  | `GenerationOptions.DEFAULT` = (maxTokens=2048, temperature=0.1, topP=1.0, modelNameOverride=null) |
| G15  | Multi-model routing via future `RoutingLlmAdapter` pattern (mirrors `HybridRetrievalAdapter`)    |
| G16  | `OutputGuardrailsPort` receives text only; `CitationPort` receives text + `AssembledContext`     |
| M01  | `ConversationHistoryPort` is read-only; writing history is a separate concern                    |
| M02  | `conversationId` is optional on `GenerationRequest`; null = stateless generation                 |
| M03  | Memory window size (`app.conversation.memory-window-size`) is application config, not per-request |
| M04  | `InMemoryConversationHistoryAdapter` is the P04.10 seam; replaceable without domain/app changes  |
| C01  | `PositionalCitationAdapter` parses `[SOURCE:N]` via hand-written scan, not regex                 |
| C02  | Marker resolution keys off `AssembledChunk.position()`, not list index                           |
| C03  | Duplicate marker references dedupe by `chunkId`, keeping first-appearance order                  |
| C04  | Malformed/out-of-range markers are silently ignored; `CitationPort` never throws                 |
| C05  | `PositionalCitationAdapter` is the sole `CitationPort` implementation; passthrough seam removed  |
| GR01 | `OutputGuardrailsPort` stays text-only (G16); finish-reason-aware blocking deferred              |
| GR02 | Null/blank generated text resolves to `GuardrailResult.block(...)`; `apply()` never throws       |
| GR03 | Oversized responses are truncated and `PASS`ed, not blocked                                      |
| GR04 | Malformed-output normalisation strips non-printable control chars only (not `\r\n\t`)             |
| GR05 | `PolicyBasedOutputGuardrailsAdapter` is the sole `OutputGuardrailsPort` implementation            |
| R01  | `PersistentConversationHistoryAdapter` is the sole `ConversationHistoryPort` impl; reads via `ConversationRepository` |
| R02  | `OllamaLlmAdapter` retries transient failures (3 attempts, exponential backoff) — reconciles G13  |
| R03  | `roles.created_at` schema drift fixed via `V017` migration, not documented as debt                |
| R04  | `test` profile's `flyway.clean-on-validation-error` removed (property no longer exists)          |
| R05  | `--enable-preview` removed from Gradle build; zero preview features were ever used                |
| O01  | `RagOrchestrationService` is the sole cross-cutting turn coordinator; existing services unchanged |
| O02  | `addAssistantMessage` mirrors `addUserMessage` exactly — same ownership check, same event         |
| O03  | REST API versioned by `/api/v1` path prefix; errors are RFC 7807 `ProblemDetail` via one handler  |
| O04  | `RetrievalResult` carries `effectiveQueryText`; `RetrievalPort` interface itself unchanged         |
| O05  | Temporary permissive `SecurityFilterChain` (ADR-approved) — replaced outright in P05.2, not layered |
| A01  | JWT access tokens are signed/verified with HS256 (symmetric key); RS256 deferred                  |
| A02  | Login issues an access token only; no refresh/logout endpoint yet                                 |
| A03  | `AuthenticateUserUseCase` (application) verifies identity; only `JwtTokenProvider` (api) mints a JWT |
| A04  | `JwtAuthenticationFilter` never rejects a request; only `RestAuthenticationEntryPoint` returns 401 |
| A05  | `tenantId`/`userId` removed from request DTOs; both now come from the validated JWT               |
| A06  | `AuthenticateUserUseCase` always hashes-and-compares (dummy hash for unknown users) to avoid a login timing side channel |
| AZ01 | `AuthorizationInterceptor` (`HandlerInterceptor`) is the sole role-decision point; never re-validates ownership |
| AZ02 | Role policy: `VIEWER`/`AUDITOR` read-only; `USER`/`ADMIN` may create conversations and send messages |
| AZ03 | `AccessDeniedException` → 403 handled by `GlobalExceptionHandler`, not a security-layer `AccessDeniedHandler`; `ADMIN` does not bypass ownership |
| TN01 | Tenant isolation is a defensive post-fetch check in `ConversationApplicationService` (extends ADR R01's pattern) |
| OW01 | Resource ownership stays at `findByIdAndUserId` (pre-existing); mismatch resolves to 404, never 403 |

---

## RAG Orchestration Pipeline (P05.1 — current, outermost)

```
HTTP POST /api/v1/conversations/{id}/messages
       │
       ▼
ConversationController.sendMessage()         ← api.controller (thin: validate, convert, invoke, convert)
       │ @RequireRole({USER, ADMIN}) enforced by AuthorizationInterceptor before this method runs (P05.3, ADR AZ01/AZ02)
       │ SendMessageCommand
       ▼
RagOrchestrationService.handleUserMessage()  ← application.orchestration (P05.1, ADR O01)
       │
       ├─▶ ConversationApplicationService.addUserMessage()        — ownership + tenant check, persist user turn (ADR TN01/OW01)
       ├─▶ RetrievalService.retrieve()                             — ranked chunks + effectiveQueryText (ADR O04)
       ├─▶ ContextAssemblyPort.assemble()                          — AssembledContext
       ├─▶ GenerationService.generate()                            — see "Generation Pipeline" below
       └─▶ ConversationApplicationService.addAssistantMessage()   — ownership + tenant check, persist assistant turn (ADR O02/TN01)
       │
       ▼
GeneratedAnswerResponse (DTO — never a domain model, ADR O03)
```

Errors from any step surface as an RFC 7807 `ProblemDetail` via `api.exception.GlobalExceptionHandler` (ADR O03). As of P05.2, every request to this endpoint requires a valid JWT — `api.config.SecurityConfig`'s temporary permissive seam (ADR O05) has been replaced outright, not layered on top of. As of P05.3, a valid JWT is not sufficient by itself — see "Authorization Pipeline" below.

---

## Authentication Pipeline (P05.2 — new)

```
HTTP POST /api/v1/auth/login {tenantId, email, password}
       │
       ▼
AuthController.login()                        ← api.controller
       │
       ├─▶ AuthenticateUserUseCase.execute()   ← application.user — verifies email/password/tenantId/active
       │       (throws InvalidCredentialsException on any failure — same exception for every cause)
       │
       └─▶ JwtTokenProvider.generateAccessToken()  ← api.security — mints an HS256 access token (ADR A01)
       │
       ▼
LoginResponse {accessToken, tokenType: "Bearer", expiresInMs}


HTTP <any other request>  Authorization: Bearer <token>
       │
       ▼
JwtAuthenticationFilter                        ← api.security (ADR A04: never rejects, only populates)
       │ valid token → SecurityContext gets a JwtAuthenticationToken(userId, tenantId, authorities)
       │ missing/invalid token → SecurityContext stays empty, request continues
       ▼
Spring Security AuthorizationFilter             ← permitAll for login/health/docs, authenticated() otherwise
       │ unauthenticated + protected endpoint → RestAuthenticationEntryPoint → 401 ProblemDetail
       ▼
ConversationController                          ← reads tenantId/userId from JwtAuthenticationToken (ADR A05),
                                                    never from the request body
```

---

## Authorization Pipeline (P05.3 — new)

```
HTTP <any /api/v1/** request except /api/v1/auth/**>
       │  SecurityContext already holds a JwtAuthenticationToken (Authentication Pipeline above)
       ▼
AuthorizationInterceptor.preHandle()      ← api.security, registered by api.config.WebMvcConfig (ADR AZ01)
       │  no @RequireRole on the target method → permitted, unconditionally
       │  @RequireRole present → principal's authorities must intersect the required roles
       │  role insufficient → AccessDeniedException
       ▼
ConversationController.<method>()          ← invoked only if the interceptor returned true
       │
       ▼
ConversationApplicationService.<method>()  ← findByIdAndUserId (ownership, pre-existing) +
                                              requireTenantMatch (tenant, new — ADR TN01)
       │  not found OR wrong tenant OR wrong owner → ResourceNotFoundException (404, never 403 — ADR OW01)
       ▼
<normal response>
```

`AccessDeniedException` (from the interceptor) and `ResourceNotFoundException` (from the ownership/tenant check) both surface via the existing `api.exception.GlobalExceptionHandler` (ADR O03/AZ03) — 403 and 404 respectively. No role, including `ADMIN`, bypasses the ownership/tenant check (ADR AZ03). Fine-grained, metadata-based content filtering integrated into the retrieval pipeline itself (`ROADMAP.md`'s "Full Authorization Filter") remains unbuilt — this pipeline is the REST-boundary half only.

---

## Generation Pipeline (P04.10)

```
GenerationRequest (with optional conversationId)
       │
       ▼
ConversationHistoryPort.getRecentMessages()  ← PersistentConversationHistoryAdapter (P04.13: reads via ConversationRepository)
       │ List<Message> (empty when conversationId is null, unknown, or belongs to another tenant)
       ▼
PromptBuilderPort.build()           ← TemplateBasedPromptBuilderAdapter
       │ PromptRequest (memoryMessages now populated)
       ▼
LlmPort.generate()                  ← OllamaLlmAdapter (inserts memory msgs between system + user)
       │ LlmResponse
       ▼
OutputGuardrailsPort.apply()        ← PolicyBasedOutputGuardrailsAdapter (P04.12: blocks null/blank, strips control chars, truncates oversized text)
       │ GuardrailResult
       ▼
CitationPort.resolve()              ← PositionalCitationAdapter (P04.11: parses [SOURCE:N], resolves against AssembledContext)
       │ List<Citation>
       ▼
GeneratedResponse
```

`tools` remain an empty passthrough (populated at agent milestone).

---

## Generation Pipeline (P04.9)

```
GenerationRequest
       │
       ▼
PromptBuilderPort.build()           ← TemplateBasedPromptBuilderAdapter
       │ PromptRequest
       ▼
LlmPort.generate()                  ← OllamaLlmAdapter
       │ LlmResponse
       ▼
OutputGuardrailsPort.apply()        ← PassthroughOutputGuardrailsAdapter (seam → P04.11)
       │ GuardrailResult
       ▼
CitationPort.resolve()              ← PassthroughCitationAdapter (seam → P04.11)
       │ List<Citation>
       ▼
GeneratedResponse
```

`memoryMessages` and `tools` are empty `List.of()` stubs; populated in P04.10 and agent milestone respectively, without changing port signatures.

---

## Infrastructure Wiring

- `HybridRetrievalAdapter` is `@Primary`; `WeaviateRetrievalAdapter` is `@Qualifier("vectorRetrieval")`; `PostgresBm25RetrievalAdapter` is `@Qualifier("bm25Retrieval")`
- `OllamaLlmAdapter` is the sole `LlmPort` implementation
- `PersistentConversationHistoryAdapter` is the sole `ConversationHistoryPort` implementation (reads via `ConversationRepository`, tenant-checked — P04.13)
- `PolicyBasedOutputGuardrailsAdapter` implements `OutputGuardrailsPort` (blocks null/blank output, strips control characters, truncates to `app.guardrails.max-response-length` — P04.12)
- `PositionalCitationAdapter` implements `CitationPort` (parses `[SOURCE:N]` markers, resolves against `AssembledContext` by `AssembledChunk.position()` — P04.11)
- `RagOrchestrationService` is the sole caller wiring `ConversationApplicationService` + `RetrievalService` + `ContextAssemblyPort` + `GenerationService` together (P05.1, ADR O01)
- `ConversationController` is the sole REST entry point for conversations (`/api/v1/conversations`); `AuthController` is the sole token-issuing entry point (`/api/v1/auth/login`, P05.2); `GlobalExceptionHandler` is the sole `@RestControllerAdvice` (P05.1, ADR O03)
- `SecurityConfig` requires a valid JWT on every endpoint except `/api/v1/auth/login`, `/actuator/health`, and the Swagger/OpenAPI paths — real HS256 validation as of P05.2 (ADR A01), replacing the P05.1 permissive seam outright (ADR O05)
- `JwtTokenProvider` (`api.security`) is the sole component that signs or verifies tokens; `JwtAuthenticationFilter` is the sole component that populates the `SecurityContext`; `RestAuthenticationEntryPoint` is the sole source of a 401 response (ADR A04)
- `AuthenticateUserUseCase` (`application.user`) is the sole verifier of login credentials, via the existing `UserRepository` port and a `BCryptPasswordEncoder` (`infrastructure.config.PasswordEncoderConfig`) — always throws the same `InvalidCredentialsException` regardless of failure cause (ADR A03)
- `springdoc-openapi-starter-webmvc-ui` auto-exposes `/v3/api-docs` and `/swagger-ui.html` from the same controller/DTO annotations — no separate spec to keep in sync (P05.1)
- `AuthorizationInterceptor` (`api.security`), registered by `WebMvcConfig` (`api.config`) against every `/api/v1/**` route, is the sole role-authorization decision point — reads `@RequireRole` off the target controller method (P05.3, ADR AZ01)
- `ConversationApplicationService.getConversation`/`.addUserMessage`/`.addAssistantMessage` are the sole tenant/ownership decision points for conversations — a private `requireTenantMatch` helper, extending the ADR R01 pattern, runs after every ownership-scoped fetch (P05.3, ADR TN01/OW01)
- `GlobalExceptionHandler` gained `AccessDeniedException` → 403 (P05.3, ADR AZ03) alongside its existing mappings

---

## Package Structure (production code)

```
com.mudassirshahzad.eka
├── domain
│   ├── chunk                        — ChunkId, Chunk
│   ├── conversation                 — Message, MessageRole, Citation
│   ├── document                     — DocumentId, Document
│   ├── generation
│   │   ├── exception                — LlmException
│   │   ├── model                    — FinishReason, GenerationOptions, LlmRequest, LlmResponse,
│   │   │                              GuardrailStatus, GuardrailResult, GeneratedResponse,
│   │   │                              PromptBuildRequest, PromptRequest, ToolDefinition
│   │   └── port                     — LlmPort, OutputGuardrailsPort, CitationPort, PromptBuilderPort
│   ├── retrieval
│   │   ├── model                    — RetrievalOptions, RetrievedChunk, RetrievalResult,
│   │   │                              SearchMetadata, AssembledChunk, AssembledContext
│   │   └── port                     — RetrievalPort, RankingPort, QueryRewritePort, ContextAssemblyPort
│   └── shared                       — TenantId
├── application
│   ├── generation                   — GenerationRequest, GenerationException, GenerationService
│   ├── orchestration                — SendMessageCommand, RagTurnResult, RagOrchestrationService (P05.1)
│   ├── retrieval                    — RetrievalRequest, RetrievalException,
│   │                                  InvalidRetrievalRequestException, RetrievalService
│   └── user                         — RegisterUserUseCase, GetUserUseCase, DeactivateUserUseCase,
│                                      AuthenticateUserUseCase (P05.2, ADR A03), UserApplicationService
├── infrastructure
│   ├── citation                     — PositionalCitationAdapter
│   ├── context                      — DefaultContextAssemblyAdapter
│   ├── conversation                 — PersistentConversationHistoryAdapter
│   ├── guardrails                   — PolicyBasedOutputGuardrailsAdapter
│   ├── llm
│   │   ├── exception                — LlmTimeoutException, LlmRateLimitException,
│   │   │                              LlmProviderUnavailableException, LlmInvalidResponseException,
│   │   │                              LlmModelNotFoundException
│   │   └── ollama                   — OllamaLlmAdapter
│   ├── prompt                       — TemplateBasedPromptBuilderAdapter
│   ├── query.rewrite                — OllamaQueryRewriteAdapter, QueryRewriteException
│   ├── ranking                      — RrfRankingAdapter
│   └── retrieval
│       ├── hybrid                   — HybridRetrievalAdapter, HybridRetrievalException
│       ├── postgres                 — PostgresBm25RetrievalAdapter, Bm25MetadataFilterTranslator,
│       │                              Bm25ScoreNormalizer
│       └── weaviate                 — WeaviateRetrievalAdapter, WeaviateVectorStoreAdapter
└── api                              — first REST surface (P05.1)
    ├── config                       — SecurityConfig (real JWT validation, P05.2 — ADR A01/O05),
    │                                  OpenApiConfig, WebMvcConfig (P05.3 — registers AuthorizationInterceptor)
    ├── controller                   — ConversationController (createConversation/sendMessage now
    │                                  @RequireRole-annotated — P05.3, ADR AZ02), AuthController (P05.2)
    ├── dto                          — CreateConversationRequest, SendMessageRequest (both
    │                                  identity-free — ADR A05), ConversationResponse,
    │                                  ConversationDetailResponse, MessageResponse, CitationResponse,
    │                                  GeneratedAnswerResponse, LoginRequest, LoginResponse (P05.2)
    ├── security                     — JwtProperties, JwtTokenProvider, JwtAuthenticationToken,
    │                                  JwtAuthenticationFilter, RestAuthenticationEntryPoint (P05.2),
    │                                  RequireRole, AuthorizationInterceptor (P05.3, ADR AZ01)
    └── exception                    — GlobalExceptionHandler (ADR O03; AccessDeniedException → 403, ADR AZ03)
```

*(P04.13 correction: `ranking` and `context` were shown incorrectly/missing above — they are top-level `infrastructure` packages, not nested under `infrastructure.retrieval`.)*

### Repository Scope (P04.13.3)

This file's Milestone/ADR tracking above covers the **retrieval/generation pipeline** (P04.x). It does not cover a second, larger body of code — pre-existing Phase 1/2 foundation work (`docs/roadmap.md`) that predates the P04.x milestone-tracking discipline. Full detail on that layer is not duplicated here; this section exists solely so its existence and status are unambiguous.

| Package (domain / application / infrastructure) | Contents | Status |
|---|---|---|
| `domain.document`, `domain.chunk` | `Document`, `Chunk` aggregates | Used by both threads |
| `domain.user`, `domain.query` | `User`, `KnowledgeQuery` aggregates | Foundation-only |
| `application.document` | `ChunkingService`, `EmbeddingService`, `DocumentIndexingService`, ingestion use cases | Foundation-only, self-contained ingestion pipeline |
| `application.conversation` | `ConversationApplicationService` + CRUD use cases | Write side of P04.13's `ConversationHistoryPort` fix (ADR R01); **now also reachable via REST** — `ConversationController` calls `createConversation`/`getConversation` directly and indirectly via `RagOrchestrationService` (P05.1) |
| `application.chat`, `application.query` | Chat session and knowledge-query use cases | Foundation-only, not yet wired to `application.generation`/`application.retrieval` (see P04.13.8) |
| `application.user` | User registration/lookup/role/password use cases, plus `AuthenticateUserUseCase` (P05.2) | **Partially reachable via REST as of P05.2** — `AuthController` calls `AuthenticateUserUseCase` for login; `RegisterUserUseCase`, role management, and password change remain unreached (no admin/registration endpoint yet) |
| `application.event` + `infrastructure.event` | 17 domain event records + `SpringDomainEventPublisher` | Built, unused — no `@EventListener` anywhere (see P04.13.8) |
| `infrastructure.parsing`, `.embedding`, `.storage`, `.vectorstore` | Tika parsing, Ollama embedding, local file storage, Weaviate vector store (ingestion side) | Foundation-only ingestion adapters |
| `infrastructure.persistence.postgres` | 8 repository adapters, 12 entities, 7 mappers, 11 JPA repositories | Foundation-only; now has baseline tests (P04.13.4) |
| `infrastructure.config` | `DatabaseConfig`, `AsyncConfig`, `AppProperties`, `PasswordEncoderConfig` (P05.2) | Spring wiring for the foundation layer |

**Update (P05.1):** `com.mudassirshahzad.eka.api.controller.ConversationController` is now the first REST entry point (`/api/v1/conversations`), reaching `application.conversation` and `application.orchestration` directly. `application.chat`, `application.query`, and `application.document` remain unreached — no endpoint calls them yet, consistent with the P04.13.8 classification below (chat/query integration is P05-future, not this milestone).

**Update (P05.2):** `com.mudassirshahzad.eka.api.controller.AuthController` (`/api/v1/auth/login`) is a second REST entry point, reaching `application.user` for the first time — but only its new `AuthenticateUserUseCase`. There is still no registration/admin endpoint, so users must be seeded directly (e.g. via a migration or a one-off script) until a future milestone adds one; that gap is a known, accepted limitation of "Authentication Foundation," not an oversight.

**Update (P05.3):** `ConversationApplicationService.getConversation`/`.addUserMessage`/`.addAssistantMessage` (the three REST-reachable methods) now take and verify `TenantId` (ADR TN01). `.renameConversation` and `.deleteConversation` — still unreached by any endpoint (no rename/delete route exists) — were deliberately **not** given the same check; see Deferred Items below.

### Deferred Items (P04.13.8)

Reviewed without implementing — each classified so none of these become a future undocumented surprise:

| Item | Classification | Notes |
|---|---|---|
| Domain event system (17 events, zero `@EventListener` consumers) | Future roadmap | Not broken — built ahead of its consumers. Revisit when an analytics/audit/notification feature needs it. |
| `application.chat` (ChatSession) not wired to `application.generation` | Future roadmap (P04.14 — End-to-End RAG) | `RecordTurnCommand` is designed to receive exactly what `LlmResponse` already produces; natural fit for the next milestone, not this one. |
| `application.query` (KnowledgeQuery) not wired to `application.retrieval` | Future roadmap (P04.14 — End-to-End RAG) | Same reasoning — audit/tracking layer built ahead of the entry point that would call it. |
| `UploadDocumentUseCase`'s `@Transactional` spanning Tika/Ollama/Weaviate calls | Genuine technical debt | Real connection-pool-exhaustion and dual-write risk; not urgent (ingestion has no external caller yet) but should be fixed before ingestion is load-bearing. |
| `AppProperties` (`@ConfigurationProperties`) vs. scattered `@Value` config binding | No action required | Both patterns are valid Spring idioms already in active use; forcing one convention across every adapter is cosmetic churn without measurable long-term value (rejected per review philosophy). |
| `ConversationApplicationService.renameConversation`/`.deleteConversation` lack the P05.3 tenant check (ADR TN01) | Genuine technical debt (P05.3) | Unreached by any REST endpoint today, so no live exposure — but the same implicit-tenant-invariant gap ADR TN01 fixed elsewhere in this class still exists here. Close this the moment either method gets a route. |
| No registration/admin endpoint for `application.user` | Known limitation (P05.2, still true) | Users must be seeded directly; `RegisterUserUseCase` remains unwired to REST. |

---

## Build Tool

Gradle 8.12 — no `gradlew` wrapper present.

Binary: `~/.gradle/wrapper/dists/gradle-8.12-bin/cetblhg4pflnnks72fxwobvgv/gradle-8.12/bin/gradle`

Java 21. `--enable-preview` removed in P04.13 (ADR R05) — no preview language feature was ever used.

---

## Security / Logging Policy

Architecture constraint — enforced across all milestones:

| Data           | Allowed |
|----------------|---------|
| Tenant ID      | DEBUG / WARN / ERROR |
| Model name     | DEBUG |
| Finish reason  | DEBUG |
| Token counts   | DEBUG |
| Latency ms     | DEBUG |
| System text    | NEVER |
| User query     | NEVER |
| Chunk content  | NEVER |
| Generated text | NEVER |
| Prompt content | NEVER |
