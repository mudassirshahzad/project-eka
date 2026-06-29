# Changelog

All notable changes to Project EKA are documented here.
For detailed release notes see [docs/releases/](docs/releases/).

## [Unreleased] — v0.5.x Retrieval Foundation

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
