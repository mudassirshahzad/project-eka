# Project State

Current Version

v0.5.1 (In Progress) — Phase 5: Application Platform

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

**Grand total tests: 530 — 0 failures**

---

## Current Milestone

**P05.2 — Authentication Foundation** ← next to implement

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

---

## RAG Orchestration Pipeline (P05.1 — current, outermost)

```
HTTP POST /api/v1/conversations/{id}/messages
       │
       ▼
ConversationController.sendMessage()         ← api.controller (thin: validate, convert, invoke, convert)
       │ SendMessageCommand
       ▼
RagOrchestrationService.handleUserMessage()  ← application.orchestration (P05.1, ADR O01)
       │
       ├─▶ ConversationApplicationService.addUserMessage()        — persist user turn
       ├─▶ RetrievalService.retrieve()                             — ranked chunks + effectiveQueryText (ADR O04)
       ├─▶ ContextAssemblyPort.assemble()                          — AssembledContext
       ├─▶ GenerationService.generate()                            — see "Generation Pipeline" below
       └─▶ ConversationApplicationService.addAssistantMessage()   — persist assistant turn (ADR O02)
       │
       ▼
GeneratedAnswerResponse (DTO — never a domain model, ADR O03)
```

Errors from any step surface as an RFC 7807 `ProblemDetail` via `api.exception.GlobalExceptionHandler` (ADR O03). Every request currently reaches the controller unauthenticated — `api.config.SecurityConfig` is a deliberate, temporary, ADR-approved seam (ADR O05) replaced outright in P05.2.

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
- `ConversationController` is the sole REST entry point (`/api/v1/conversations`); `GlobalExceptionHandler` is the sole `@RestControllerAdvice` (P05.1, ADR O03)
- `SecurityConfig` permits every request — temporary, ADR-approved, replaced outright in P05.2 (ADR O05)
- `springdoc-openapi-starter-webmvc-ui` auto-exposes `/v3/api-docs` and `/swagger-ui.html` from the same controller/DTO annotations — no separate spec to keep in sync (P05.1)

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
│   └── retrieval                    — RetrievalRequest, RetrievalException,
│                                      InvalidRetrievalRequestException, RetrievalService
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
    ├── config                       — SecurityConfig (temporary, ADR O05), OpenApiConfig
    ├── controller                   — ConversationController
    ├── dto                          — CreateConversationRequest, SendMessageRequest,
    │                                  ConversationResponse, ConversationDetailResponse,
    │                                  MessageResponse, CitationResponse, GeneratedAnswerResponse
    └── exception                    — GlobalExceptionHandler (ADR O03)
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
| `application.chat`, `application.query`, `application.user` | Chat session, knowledge-query, and user-management use cases | Foundation-only, not yet wired to `application.generation`/`application.retrieval` (see P04.13.8) |
| `application.event` + `infrastructure.event` | 17 domain event records + `SpringDomainEventPublisher` | Built, unused — no `@EventListener` anywhere (see P04.13.8) |
| `infrastructure.parsing`, `.embedding`, `.storage`, `.vectorstore` | Tika parsing, Ollama embedding, local file storage, Weaviate vector store (ingestion side) | Foundation-only ingestion adapters |
| `infrastructure.persistence.postgres` | 8 repository adapters, 12 entities, 7 mappers, 11 JPA repositories | Foundation-only; now has baseline tests (P04.13.4) |
| `infrastructure.config` | `DatabaseConfig`, `AsyncConfig`, `AppProperties` | Spring wiring for the foundation layer |

**Update (P05.1):** `com.mudassirshahzad.eka.api.controller.ConversationController` is now the first REST entry point (`/api/v1/conversations`), reaching `application.conversation` and `application.orchestration` directly. `application.chat`, `application.query`, `application.document`, `application.user`, and `application.event` remain unreached — no endpoint calls them yet, consistent with the P04.13.8 classification below (chat/query integration is P05-future, not this milestone).

### Deferred Items (P04.13.8)

Reviewed without implementing — each classified so none of these become a future undocumented surprise:

| Item | Classification | Notes |
|---|---|---|
| Domain event system (17 events, zero `@EventListener` consumers) | Future roadmap | Not broken — built ahead of its consumers. Revisit when an analytics/audit/notification feature needs it. |
| `application.chat` (ChatSession) not wired to `application.generation` | Future roadmap (P04.14 — End-to-End RAG) | `RecordTurnCommand` is designed to receive exactly what `LlmResponse` already produces; natural fit for the next milestone, not this one. |
| `application.query` (KnowledgeQuery) not wired to `application.retrieval` | Future roadmap (P04.14 — End-to-End RAG) | Same reasoning — audit/tracking layer built ahead of the entry point that would call it. |
| `UploadDocumentUseCase`'s `@Transactional` spanning Tika/Ollama/Weaviate calls | Genuine technical debt | Real connection-pool-exhaustion and dual-write risk; not urgent (ingestion has no external caller yet) but should be fixed before ingestion is load-bearing. |
| `AppProperties` (`@ConfigurationProperties`) vs. scattered `@Value` config binding | No action required | Both patterns are valid Spring idioms already in active use; forcing one convention across every adapter is cosmetic churn without measurable long-term value (rejected per review philosophy). |

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
