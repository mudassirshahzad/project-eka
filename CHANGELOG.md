# Changelog

All notable changes to Project EKA are documented here.
For detailed release notes see [docs/releases/](docs/releases/).

## [Unreleased] — v0.5.x Retrieval Foundation

### Added (P04.9 — Chat Generation)

- `LlmException` — domain base exception in `domain.generation.exception`; infrastructure subtypes extend this so all LLM errors are catchable at the domain boundary without importing infrastructure types
- `LlmTimeoutException`, `LlmRateLimitException`, `LlmProviderUnavailableException`, `LlmInvalidResponseException`, `LlmModelNotFoundException` — five infrastructure subtypes in `infrastructure.llm.exception`; `LlmProviderUnavailableException` is the catch-all wrap for unknown adapter failures
- `FinishReason` — domain enum: `STOP`, `LENGTH`, `TOOL_CALL`, `CONTENT_FILTER`, `ERROR`; unknown provider finish reason strings default to `STOP` (defensive mapping)
- `GenerationOptions` — domain record: `maxTokens` (default 2048), `temperature` (default 0.1), `topP` (default 1.0), `modelNameOverride` (default null); compact constructor validates ranges (`maxTokens ≥ 1`, `temperature ∈ [0.0, 2.0]`, `topP ∈ [0.0, 1.0]`); `GenerationOptions.DEFAULT` constant
- `LlmRequest` — domain record wrapping `PromptRequest` + `GenerationOptions`; null guards on both fields (ADR G09)
- `LlmResponse` — domain record: `generatedText`, `finishReason`, `modelName`, `promptTokens`, `completionTokens`, `latencyMs`; derived `totalTokens()` method; null guards on text, reason, and model name (ADR G10)
- `GuardrailStatus` — domain enum: `PASS`, `BLOCK`
- `GuardrailResult` — domain record: `status` + `text`; factory methods `pass(String text)` and `block(String safeText)`; query methods `isPassed()` and `isBlocked()`
- `GeneratedResponse` — domain record: `generatedText`, `citations` (defensively copied to unmodifiable list), `modelName`, `totalTokens`, `latencyMs`; query method `hasCitations()`
- `LlmPort` — domain port: `LlmResponse generate(LlmRequest request)`; synchronous per ADR G11; streaming deferred to future `StreamingLlmPort`
- `OutputGuardrailsPort` — domain port: `GuardrailResult apply(String generatedText, TenantId tenantId)`; receives generated text only per ADR G16
- `CitationPort` — domain port: `List<Citation> resolve(String generatedText, AssembledContext context, TenantId tenantId)`; receives text + `AssembledContext` passenger per ADR G16
- `OllamaLlmAdapter` — primary `LlmPort` implementation; converts `LlmRequest` to Spring AI `Prompt` (single conversion point, no Spring AI types leak to domain); maps all five `FinishReason` values; wraps provider exceptions as `LlmProviderUnavailableException`; reads `promptTokens`/`completionTokens` as `Integer` (Spring AI 1.0.0 Usage API); extracts model name from `ChatResponseMetadata.getModel()`; uses `OllamaOptions.builder()` with `model`, `temperature`, `numPredict`, `topP`; resolves active model from `GenerationOptions.modelNameOverride` or falls back to `${spring.ai.ollama.chat.model:qwen3}`; all metadata extraction is try-catch wrapped for resilience; generated text, prompt text, and LLM response content are never logged (ADR G13, G14, G15)
- `PassthroughOutputGuardrailsAdapter` — named seam implementing `OutputGuardrailsPort`; always returns `GuardrailResult.pass(generatedText)`; explicit architectural placeholder for P04.11 Output Guardrails
- `PassthroughCitationAdapter` — named seam implementing `CitationPort`; always returns `List.of()`; explicit architectural placeholder for P04.11 Citation Generation
- `GenerationRequest` — application-layer record: `assembledContext`, `originalQueryText` (validated non-blank), `tenantId`, `options` (normalised to `GenerationOptions.DEFAULT` if null)
- `GenerationException` — application-layer exception extending `ApplicationException`
- `GenerationService` — pure orchestration service; calls ports in sequence: `PromptBuilderPort.build()` → `LlmPort.generate()` → `OutputGuardrailsPort.apply()` → `CitationPort.resolve()`; `AssembledContext` is carried as a read-only passenger from `GenerationRequest` to `CitationPort` without modification; no Spring AI types imported — provider independence is structural; `memoryMessages` and `tools` are empty `List.of()` stubs (populated in P04.10 and agent milestone respectively)
- ADRs G09–G16 frozen (documented in architecture review artifact preceding this milestone)
- 82 new tests across 9 test classes — `GenerationOptionsTest` (12): range validation, DEFAULT constant; `LlmRequestTest` (5): null guards, field preservation; `LlmResponseTest` (8): null guards, totalTokens derived; `GuardrailResultTest` (5): factory methods, query methods; `GeneratedResponseTest` (8): null guards, defensive copy, hasCitations; `OllamaLlmAdapterTest` (14): all FinishReason mappings, token counts, model override, error wrapping, prompt forwarding; `PassthroughOutputGuardrailsAdapterTest` (5): null guards, always PASS; `PassthroughCitationAdapterTest` (5): null guards, always empty list; `GenerationServiceTest` (20): constructor null guards, pipeline ordering, guardrail block path, LLM exception propagation, AssembledContext passenger wiring, default options; 413 total tests, 0 failures

### Added (P04.8 — Prompt Builder)

- `ToolDefinition` — stub domain record (`name`, `description`) reserving the tool-calling contract for the agent milestone; both `PromptBuildRequest` and `PromptRequest` carry `List<ToolDefinition>` as an empty passthrough today per ADR G07; no prompt injection or execution occurs in P04.8
- `PromptBuildRequest` — frozen single-input domain record for `PromptBuilderPort` (ADR G08); fields: `assembledContext`, `originalQueryText`, `memoryMessages` (empty today, populated in P04.10), `tools` (empty today, populated at agent milestone), `tenantId`; null `memoryMessages` and null `tools` are normalised to `List.of()` so call sites are forward-compatible with P04.10 without a port-signature change; both lists are defensively copied
- `PromptRequest` — provider-independent prompt specification returned by `PromptBuilderPort` (ADR G01); carries `systemText` (fully rendered, including assembled context), `userText` (original verbatim query per ADR G02), `memoryMessages`, and `tools`; contains no Spring AI, Ollama, or OpenAI types — conversion to `org.springframework.ai.chat.prompt.Prompt` is the exclusive responsibility of the LLM adapter (P04.9)
- `PromptBuilderPort` — domain port with frozen signature `PromptRequest build(PromptBuildRequest request)`; all future inputs (memory, tools, persona, locale) travel inside `PromptBuildRequest` — the interface itself never grows
- `TemplateBasedPromptBuilderAdapter` — production implementation of `PromptBuilderPort`; loads `classpath:prompts/qa-system.txt` at startup and fails fast with `IllegalStateException` if the template is absent or unreadable (ADR G06: fail fast, no silent fallback); renders the context block by prefixing each `AssembledChunk` with `[SOURCE:N]` where `N = chunk.position() + 1` (1-based, ADR G03) and separating chunks with a blank line; replaces the `{context}` placeholder in the template; passes `memoryMessages` and `tools` through to `PromptRequest` unchanged; stateless — same inputs always produce same output (ADR G04); never logs system text, user query, or chunk content
- `src/main/resources/prompts/qa-system.txt` — external QA system prompt template; contains rules for context-grounded answering, source citation with `[SOURCE:N]` markers, and a `{context}` placeholder that is replaced at runtime; template is external to Java classes per ADR G06
- 50 new tests across 4 test classes — `ToolDefinitionTest` (6): name/description validation; `PromptBuildRequestTest` (10): null guards, null-tolerance for optional lists, defensive copy, immutability, field preservation; `PromptRequestTest` (9): null guards, null-tolerance, defensive copy, immutability, field preservation; `TemplateBasedPromptBuilderAdapterTest` (25): template loading, missing template fail-fast, null request guard, user text is original query (not rewritten), empty-context fallback, single and multi-chunk SOURCE markers, 5-chunk marker completeness, chunk ordering, source-before-content ordering, double-newline separator, verbatim content, passthrough of memory and tools, unmodifiable result lists, placeholder always replaced, tenant ID not leaked into prompt text, `renderContext` edge cases; 331 total tests, 0 failures

### Added (P04.7 — Context Assembly)

- `AssembledChunk` — immutable domain record carrying the full citation identity (`ChunkId`, `DocumentId`, `TenantId`), raw content, relevance score, and zero-based assembly position; position is distinct from `RetrievedChunk.rank()` — it records where the chunk appears in the assembled context, not where it appeared in raw retrieval output
- `AssembledContext` — immutable domain record produced by Context Assembly; carries the ordered list of `AssembledChunk`s, the effective query text (post-rewrite), the applied token budget, and the estimated token count of included chunks; `chunks` list is always unmodifiable; Prompt Builder must consume this object directly without accessing retrieval internals
- `ContextAssemblyPort` signature evolved from `String assemble(...)` to `AssembledContext assemble(...)` — the previous `String` return type discarded all citation metadata; since no code called this port at the time of evolution, the change is non-breaking
- `DefaultContextAssemblyAdapter` — pure Java adapter implementing the assembly rules: (1) iterate ranked chunks in input order; (2) skip chunks with a `ChunkId` already seen (deduplication); (3) stop when the next chunk would cause cumulative estimated token count to exceed `tokenBudget` (budget is strict — the overflowing chunk and all subsequent chunks are excluded to preserve ranking order); (4) stop when the number of included chunks would exceed `app.context.max-chunks`
- Token estimation uses the 4-chars-per-token heuristic: `max(1, text.length() / 4)` — deterministic and zero-dependency; precise tokenization deferred to P04.8 (requires external tokenizer)
- `app.context.max-chunks: 10` configuration property (default 10); overridable per deployment
- Assembly never calls an LLM, never generates prose, and never modifies chunk content — this is explicitly enforced by the adapter design
- 28 new tests in `DefaultContextAssemblyAdapterTest` covering: ordering and sequential positions, deduplication (keeps first occurrence), max-chunks limit, token budget enforcement, stop-at-first-overflow budget behaviour, combined dedup + max-chunks constraint, metadata preservation (all fields), immutable result list, token estimation edge cases (empty, null, short, exact heuristic), constructor validation; 281 total tests, 0 failures

### Added (P04.6 — Query Rewriting)

- `OllamaQueryRewriteAdapter` — first production implementation of `QueryRewritePort`; uses the Ollama chat model (temperature 0.1) to rewrite the user's query into retrieval-optimized terms before Hybrid Retrieval executes
- LLM rewrite strategy chosen over rule-based: semantic normalization (abbreviation expansion, conversational-to-declarative phrasing) cannot be achieved with rules; Ollama is already configured in the project and responds in <200ms locally
- Constrained system prompt prevents the model from answering the user's question; instructs it to return only the rewritten query text with no explanation, preamble, or markdown; instructs abbreviation expansion (e.g. `"SLA"` → `"service level agreement"`) and intent preservation
- Safe fallback: any LLM failure (timeout, connection error, blank response) logs a warning and returns the original query unchanged — the retrieval pipeline is never interrupted by a rewrite failure
- `app.retrieval.query-rewrite.enabled` configuration flag (default `true`) allows operators to disable rewriting in environments without Ollama or for A/B quality comparisons; when disabled, the LLM is not called
- `RetrievalService` updated to accept `QueryRewritePort` as a required constructor parameter; rewritten query is passed to both `RetrievalPort.retrieve()` and `RankingPort.rank()` for consistency
- `QueryRewriteException` — internal exception type for infrastructure-level rewrite failures (before fallback)
- 21 new tests: `RetrievalServiceTest` updated with `QueryRewritePort` mock and 5 new rewrite-integration tests; `OllamaQueryRewriteAdapterTest` covers success, blank/null fallback, disabled mode, whitespace stripping, LLM exception fallback, system prompt content, guard conditions; 253 total tests, 0 failures
- README not updated — no user-facing API surface changed; internal retrieval pipeline improvement only

### Added (P04.5 — Hybrid Retrieval Orchestration)

- `HybridRetrievalAdapter` — composite `RetrievalPort` that orchestrates vector and BM25 retrieval sequentially, concatenates results, and returns a unified `RetrievalResult` for downstream RRF ranking
- Sequential execution chosen over parallel: latency gain (~50ms) does not currently justify `CompletableFuture` complexity, exception semantics, and executor lifecycle overhead — documented for future upgrade when measured data justifies it
- Partial failure tolerance: if one engine fails, the surviving engine's results are used and `SearchMetadata.strategy()` reflects the degraded mode (`"hybrid:vector-only"` or `"hybrid:bm25-only"`); only total failure (both engines fail) raises `HybridRetrievalException`
- `HybridRetrievalException` — infrastructure exception thrown when both retrieval engines fail simultaneously
- `SearchMetadata` after fusion: `totalHits` = combined pre-deduplication count from both engines; `latencyMs` = total sequential retrieval wall-clock time; `strategy` = `"hybrid"` (or degraded variant) — `RetrievalService` preserves this metadata unchanged in the final ranked result
- Spring wiring: `HybridRetrievalAdapter` annotated `@Primary`; `WeaviateRetrievalAdapter` and `PostgresBm25RetrievalAdapter` annotated `@Qualifier("vectorRetrieval")` / `@Qualifier("bm25Retrieval")` — no new interfaces or `@Configuration` classes required
- `RetrievalService` promoted to `@Service` — now a Spring-managed bean automatically receiving `HybridRetrievalAdapter` as its `RetrievalPort`
- Duplicate handling: chunks appearing in both engine results are passed to RRF twice; deduplication and contribution accumulation remain the sole responsibility of `RrfRankingAdapter`
- 16 new tests in `HybridRetrievalAdapterTest` covering: both succeed, vector-only, BM25-only, both fail, empty results, ordering, duplicate passthrough, parameter propagation, sequential execution verification; 232 total tests, 0 failures

### Added

- Retrieval domain model: `RetrievalOptions`, `RetrievedChunk`, `RetrievalResult`, `SearchMetadata`
- Port interfaces: `RetrievalPort`, `RankingPort`, `QueryRewritePort`, `ContextAssemblyPort`
- Application layer: `RetrievalRequest`, `RetrievalService`, `RetrievalException`, `InvalidRetrievalRequestException`
- Production `WeaviateRetrievalAdapter` — first `RetrievalPort` implementation backed by Weaviate vector search
- `RetrievedChunkMapper` — infrastructure mapper from `VectorSearchResult` + `Chunk` to `RetrievedChunk`
- Production `PostgresBm25RetrievalAdapter` — second `RetrievalPort` implementation backed by PostgreSQL Full-Text Search
- `Bm25ScoreNormalizer` — max-normalization of unbounded `ts_rank` scores to `[0.0, 1.0]`; the highest-scoring result in each result set maps to `1.0` with others scaled proportionally
- `Bm25MetadataFilterTranslator` — translates `MetadataFilter` criteria (`department`, `classification`, `chunkingStrategy`, `tags`) to parameterized SQL predicates for safe, injection-free filter composition
- Mandatory tenant isolation: `WeaviateRetrievalAdapter` enforces isolation via `MetadataFilter`; `PostgresBm25RetrievalAdapter` enforces isolation via `AND c.tenant_id = :tenantId` in the SQL skeleton — neither can be bypassed by the caller
- Score normalization per adapter: Weaviate certainty scores clamped defensively to `[0.0, 1.0]`; BM25 `ts_rank` scores max-normalized over the result set (documented in `Bm25ScoreNormalizer`)
- `MetadataFilter` support: caller-supplied filter criteria composed with tenant isolation in both adapters
- `RetrievalOptions` enforcement: `topK` and `minimumScore` respected on every retrieval path
- Infrastructure exception translation: Weaviate errors wrapped as `RetrievalAdapterException`; PostgreSQL JDBC errors wrapped as `Bm25RetrievalException`
- Rank semantics established: `RetrievedChunk.rank` is the zero-based position in the raw retrieval engine output before post-filtering, preserved correctly for future RRF fusion
- 36 new automated tests (24 adapter + 12 normalizer); 182 total tests, 0 failures

### Fixed (P04.AC1 Fix #1 — Eliminate Double-Embedding)

- **Critical bug fix**: chunks were embedded twice per ingestion — once by `EmbeddingService.embed()` and again unconditionally by `WeaviateVectorStore.doAdd()` inside Spring AI 1.0.0; this doubled embedding cost and silently discarded provenance-carrying vectors
- `VectorStore.index()` port signature changed from `index(List<Chunk>)` to `index(List<Chunk>, List<float[]>)` — callers supply pre-computed vectors and the vector store no longer triggers a second `EmbeddingModel.embed()` call
- `ChunkApplicationService.saveAll()` return type changed from `List<Chunk>` to `List<EmbeddedChunk>` — embedding vectors are preserved through the persistence step and forwarded to the indexing step
- `DocumentIndexingService.index()` parameter changed from `List<Chunk>` to `List<EmbeddedChunk>` — extracts `Chunk` and `float[]` lists and passes pre-computed vectors directly to `VectorStore.index()`
- `WeaviateVectorStoreAdapter.index()` rewritten: bypasses `springVectorStore.add()` entirely; calls the Weaviate Java client batch API directly with pre-computed vectors and replicates Spring AI's storage format (`content`, `metadata`, `meta_*` fields) so that `search()` — which still delegates to Spring AI — deserialises results correctly
- `WeaviateVectorStoreAdapter.search()` null-guards the `filterExpression` before passing it to `SearchRequest.builder()` — Spring AI 1.0.0 throws on a null filter expression
- 10 new regression tests in `WeaviateVectorStoreAdapterTest` verify: Weaviate client (not Spring AI) is called; pre-computed vectors are forwarded exactly; vectorId is assigned post-success; vectorId is not assigned on batch failure; content and `meta_*` properties are stored; mismatch between chunk/vector counts is rejected; search still delegates to Spring AI; null filter expression is safe; 216 total tests, 0 failures

### Added (P04.4 — Reciprocal Rank Fusion)

- Production `RrfRankingAdapter` — first `RankingPort` implementation; merges multiple ranked retrieval lists using Reciprocal Rank Fusion (Cormack et al. 2009)
- RRF formula: `score = Σ (1 / (k + rank_i))` summed over all retrieval engine occurrences of each chunk; `k` defaults to 60 and is configurable via `app.retrieval.rrf-k`
- Duplicate chunk fusion: chunks appearing in multiple retrieval lists are merged by `ChunkId`; RRF contributions accumulate and metadata is preserved from the first occurrence
- Max-normalization of raw RRF scores to `[0.0, 1.0]` satisfying the `RetrievedChunk.score` contract; the highest-scoring chunk in each fused result set maps to `1.0`
- Deterministic tie-breaking: equal RRF scores resolved by ascending lexicographic order of `ChunkId` UUID — stable across JVM restarts
- Output rank semantics: `RetrievedChunk.rank` in the fused output is the zero-based position in the RRF-ranked list; original per-engine ranks are consumed as formula inputs
- 24 new automated tests covering mathematical correctness, duplicate accumulation, score normalization, tie-breaking, k-value sensitivity, and the classic two-list fusion example; 206 total tests, 0 failures

## [v0.4.0] — Document Ingestion

- Apache Tika multi-format document parsing with magic-byte detection
- Token-aware sliding window chunking with paragraph-boundary snapping
- Batch embedding generation via Ollama (`nomic-embed-text`, 768-dim)
- Weaviate vector indexing with idempotent re-index and delete synchronization
- Ingestion validation (vector count, duplicate detection, provenance checks)
- Ingestion benchmark service with per-phase timing and throughput metrics
- 104 automated tests, 0 failures

## [v0.3.0] — Application Layer

- Full use case layer for document, conversation, query, and user management
- Domain event system with 17 event types and Spring-backed publisher
- `UploadDocumentUseCase` — end-to-end ingestion orchestration
- `DeleteDocumentUseCase` — cascade removal with file storage cleanup
- 46 automated tests across 12 test classes

## [v0.2.0] — Persistence Foundation

- 16 Flyway schema migrations (V001–V016)
- JPA entity hierarchy (`BaseUuidEntity → AuditableEntity`)
- Spring Data repository adapters for all domain aggregates
- Multi-tenant schema design: `TenantId` on every entity
- PostgreSQL 16 with full-text search columns prepared

## [v0.1.0] — Architecture Foundation

- Hexagonal Architecture (Ports & Adapters) scaffold
- ArchUnit: 8 layering rules enforced at build time
- Domain model: Document, Chunk, Conversation, ChatSession, KnowledgeQuery, User aggregates
- Port interfaces: FileStorage, DocumentParser, VectorStore, EmbeddingProvider, DomainEventPublisher
- Spring Boot 3.5.0 / Java 21 / Gradle 8.12
- Docker Compose: PostgreSQL 16, Weaviate 1.25, Ollama
