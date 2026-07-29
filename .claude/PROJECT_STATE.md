# Project State

Current Version

v0.5.0 (In Progress)

**Namespace:** Root package is `com.mudassirshahzad.eka` (renamed from `com.mudassir.eka` in R01 — pure namespace refactor, no behavioral or architectural change).

---

## Completed Milestones

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

**Total tests: 470 — 0 failures**

---

## Current Milestone

**P04.13 — End-to-End RAG** ← next to implement

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

---

## Generation Pipeline (P04.10)

```
GenerationRequest (with optional conversationId)
       │
       ▼
ConversationHistoryPort.getRecentMessages()  ← InMemoryConversationHistoryAdapter
       │ List<Message> (empty when conversationId is null)
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
- `InMemoryConversationHistoryAdapter` is the sole `ConversationHistoryPort` implementation (seam → durable store)
- `PolicyBasedOutputGuardrailsAdapter` implements `OutputGuardrailsPort` (blocks null/blank output, strips control characters, truncates to `app.guardrails.max-response-length` — P04.12)
- `PositionalCitationAdapter` implements `CitationPort` (parses `[SOURCE:N]` markers, resolves against `AssembledContext` by `AssembledChunk.position()` — P04.11)

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
│   └── retrieval                    — RetrievalRequest, RetrievalException,
│                                      InvalidRetrievalRequestException, RetrievalService
└── infrastructure
    ├── citation                     — PositionalCitationAdapter
    ├── conversation                 — InMemoryConversationHistoryAdapter
    ├── guardrails                   — PolicyBasedOutputGuardrailsAdapter
    ├── llm
    │   ├── exception                — LlmTimeoutException, LlmRateLimitException,
    │   │                              LlmProviderUnavailableException, LlmInvalidResponseException,
    │   │                              LlmModelNotFoundException
    │   └── ollama                   — OllamaLlmAdapter
    ├── prompt                       — TemplateBasedPromptBuilderAdapter
    ├── query.rewrite                — OllamaQueryRewriteAdapter, QueryRewriteException
    └── retrieval
        ├── hybrid                   — HybridRetrievalAdapter, HybridRetrievalException
        ├── postgres                 — PostgresBm25RetrievalAdapter, Bm25MetadataFilterTranslator,
        │                              Bm25ScoreNormalizer
        ├── ranking                  — RrfRankingAdapter
        └── weaviate                 — WeaviateRetrievalAdapter, WeaviateVectorStoreAdapter
```

---

## Build Tool

Gradle 8.12 — no `gradlew` wrapper present.

Binary: `~/.gradle/wrapper/dists/gradle-8.12-bin/cetblhg4pflnnks72fxwobvgv/gradle-8.12/bin/gradle`

Java 21 with `--enable-preview` in compiler and test JVM args.

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
