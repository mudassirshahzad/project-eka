# Component Architecture

> Service responsibilities, RAG pipeline internals, sequence flows, and the pluggable extension points of Project EKA (Enterprise Knowledge Assistant).

---

## Table of Contents

- [Component Map](#component-map)
- [Ingestion Pipeline](#ingestion-pipeline)
- [Retrieval Pipeline](#retrieval-pipeline)
- [Generation Pipeline](#generation-pipeline)
- [Cross-Cutting Components](#cross-cutting-components)
- [Security Components](#security-components)
- [Pluggable Extension Points](#pluggable-extension-points)
- [Service Interaction Rules](#service-interaction-rules)
- [Sequence Diagrams](#sequence-diagrams)
- [Database Schema Relationships](#database-schema-relationships)
- [Weaviate Collection Design](#weaviate-collection-design)

---

## Component Map

```
API Layer
├── DocumentController          POST /api/v1/documents
│                               GET  /api/v1/documents/{id}
│                               DELETE /api/v1/documents/{id}
├── QueryController             POST /api/v1/query
├── ConversationController      CRUD /api/v1/conversations
├── AuthController              POST /api/v1/auth/login
│                               POST /api/v1/auth/refresh
│                               POST /api/v1/auth/logout
└── AdminController             GET  /api/v1/admin/audit-logs
                                GET  /api/v1/admin/users

Application Layer — Ingestion
├── DocumentIngestionService    Orchestrates the full ingestion pipeline
├── ChunkingService             Dispatches to format-appropriate strategy
│   ├── SemanticChunkingStrategy        PDF, DOCX, PPTX
│   ├── SentenceWindowChunkingStrategy  TXT, MD, HTML
│   └── RowChunkingStrategy             XLSX, CSV
└── EmbeddingBatchService       Manages batch size, retry, and back-pressure

Application Layer — Retrieval
├── QueryOrchestrationService   Drives the full retrieval flow
├── QueryRewritingService       Rewrites user query for search optimisation
├── HybridRetrievalService      Combines semantic + keyword results
│   ├── SemanticSearchService   ANN search via VectorStorePort
│   └── KeywordSearchService    BM25 via PostgreSQL FTS
├── ReRankingService            (Noop default) Re-scores candidates
└── ContextAssemblyService      Token-budget-aware context building

Application Layer — Generation
├── GenerationOrchestrationService  Drives prompt assembly and LLM call
├── ConversationMemoryService       Sliding-window memory management
├── PromptAssemblyService           Assembles system + context + history + user
└── SourceCitationService           Extracts [SOURCE-N] markers from response

Cross-Cutting
├── AuditService                Writes AuditLog records
├── MetadataFilterService       Validates and normalises filter criteria
└── TenantContextService        Resolves tenant from security context
```

---

## Ingestion Pipeline

The ingestion pipeline transforms a raw uploaded file into queryable vector embeddings backed by relational metadata. It is the most compute-intensive operation on the platform and runs asynchronously on a dedicated thread pool.

### Stage 1: Receipt and Storage

The controller accepts a `multipart/form-data` request, performs basic validation (file size, MIME type check against allowed list), and hands off to the ingestion service. The raw file is written to the configured storage root by `FileStoragePort`. The document aggregate is created with status `PENDING`.

**Why store the raw file?**
Re-processing becomes possible when chunking strategies improve or embedding models change. The raw file is the source of truth; derived data (chunks, vectors) are reproducible.

### Stage 2: Format Detection and Parsing

Apache Tika analyses the file's magic bytes — the actual binary header of the file, not its extension. This prevents content-type spoofing (a user naming a script `.pdf`). Tika extracts plain text and document metadata (title, author, creation date) for each format:

| Format | Tika Parser | Extracted Content |
|---|---|---|
| PDF | `PDFParser` | Text by page, document properties |
| DOCX | `OOXMLExtractorFactory` | Text with paragraph structure |
| PPTX | `OOXMLExtractorFactory` | Slide text with slide numbers |
| XLSX | `OOXMLExtractorFactory` | Cell text, sheet names |
| TXT | `TXTParser` | Raw text |
| CSV | `CSVParser` | Structured row text |
| HTML | `HtmlParser` | Tag-stripped text |
| Markdown | `TXTParser` | Raw Markdown text |

The document status transitions to `PARSING`.

### Stage 3: Chunking

`ChunkingService` selects a strategy based on `SupportedFormat`:

**SemanticChunkingStrategy** (PDF, DOCX, PPTX)
- Splits on sentence boundaries first, then respects paragraph breaks
- Target chunk size: configurable (default 512 tokens)
- Overlap: configurable (default 64 tokens)
- Preserves page number and section title in `ChunkMetadata`

**SentenceWindowChunkingStrategy** (TXT, HTML, Markdown)
- Sentence-boundary splitting with a sliding window
- Each chunk includes its surrounding context window sentences
- Effective for narrative prose and technical documentation

**RowChunkingStrategy** (XLSX, CSV)
- Groups N rows per chunk (default: N derived from token budget)
- Preserves column headers in every chunk for self-contained context
- Sheet/table name included in `ChunkMetadata`

The document status transitions to `CHUNKING`.

**Why different strategies per format?**

A PDF chapter is semantically coherent text. Applying row-based chunking to it destroys semantic boundaries. An Excel table has no sentence structure — applying semantic chunking to it produces meaningless fragments. Format-specific strategies are not an optimisation; they are a correctness requirement.

### Stage 4: Embedding

The list of chunk texts is passed to `EmbeddingBatchService`, which:

1. Splits into batches (default: 32 chunks per batch)
2. Calls `EmbeddingPort.embed(batch)` for each batch
3. Handles transient Ollama errors with exponential back-off (max 3 retries)
4. Respects Ollama's concurrent request limit

The `nomic-embed-text` model produces a 768-dimensional float vector for each chunk. The document status transitions to `EMBEDDING`.

**Why batch embedding?**
Calling `embed()` once per chunk would saturate the Ollama HTTP connection pool and produce unnecessary per-request overhead. Batching reduces total embedding time by 60–80% for typical document sizes.

### Stage 5: Vector Storage

Each chunk's text, vector, and metadata is written to Weaviate via `VectorStorePort`. The operation is a batch upsert scoped to the authenticated tenant. Weaviate returns a UUID for each stored object — this UUID is the `vector_id` that links the `chunks` table in PostgreSQL to the vector in Weaviate.

### Stage 6: Metadata Persistence

Chunk records (including `vector_id`) and the updated document record (status `INDEXED`, chunk count) are written to PostgreSQL in a single transaction. The `vector_id` FK is the reconciliation key: a scheduled job can detect orphaned vectors (Weaviate has a vector but PostgreSQL has no corresponding chunk) and clean them up.

---

## Retrieval Pipeline

The retrieval pipeline transforms a user's natural language question into a ranked list of relevant document chunks. It operates synchronously in the query serving thread.

### Stage 1: Query Rewriting

The original user query is often under-specified or context-dependent. "What did we decide about the budget?" is meaningless without the previous conversation context.

`QueryRewritingService` sends a structured prompt to Qwen3:

```
Given this conversation history:
[last N messages]

Rewrite the following query to be completely self-contained and
optimised for semantic search over a document knowledge base.
Return only the rewritten query — no explanation.

Original query: [user's query]
```

The rewritten query is stored on the `KnowledgeQuery` aggregate for traceability.

**Performance note:** Query rewriting is an extra LLM call (200–800ms). It is cached by hash of `(originalQuery + conversationId + lastMessageId)`. Cache TTL: 5 minutes. It can be disabled per-request for latency-sensitive use cases.

### Stage 2: Hybrid Search

Both search strategies run on the rewritten query:

**Semantic (ANN) Search — `SemanticSearchService`**
- Embeds the rewritten query via `EmbeddingPort`
- Calls `VectorStorePort.hybridSearch()` with `alpha=0.75` (vector-weighted)
- Pre-filter applied: `tenantId` always first; then department, classification, tags if present
- Over-fetches: retrieves top-20 candidates

**Keyword (BM25) Search — `KeywordSearchService`**
- Runs a PostgreSQL FTS query against the `chunks.content` `tsvector` index
- Uses plainto_tsquery for user-friendly query parsing
- Scoped to the same metadata filters
- Returns top-20 candidates

**Result Fusion**
- Candidates from both searches are merged and deduplicated by `chunk_id`
- Reciprocal Rank Fusion (RRF) scores are computed if both searches return the same chunk
- The unified candidate list (up to 30 unique chunks) proceeds to re-ranking

**Why over-fetch?**
ANN search optimises for approximate nearest neighbours, not precision. The first-ranked vector result is not always the most relevant result for the actual user question. Fetching 20 and ranking 5 produces significantly better precision than fetching 5 directly. This is the standard RAG retrieval pattern.

### Stage 3: Re-Ranking

`ReRankingService` scores each candidate chunk against the rewritten query using a cross-encoder model. The cross-encoder reads both the query and the chunk together, producing a relevance score that captures semantic compatibility beyond pure embedding similarity.

The top-5 candidates by relevance score are selected.

**Default implementation:** `NoopReRankingService` — returns candidates in their original ranked order. Suitable for initial deployment.

**Upgrade path:** Replace `NoopReRankingService` with:
- A local cross-encoder (BERT-based, deployed via Ollama or a Python microservice)
- Cohere Rerank API (requires `CoheReRankingAdapter` implementing `ReRankingPort`)
- A fine-tuned domain-specific re-ranker

The port interface is stable. No other code changes are required.

### Stage 4: Context Assembly

`ContextAssemblyService` converts ranked chunks into a structured context string:

```
[SOURCE-1] Document: Annual Report 2024.pdf | Page: 12 | Section: Financial Summary
"The organisation recorded revenue of £42M in FY2024, representing a 15% increase..."

[SOURCE-2] Document: Q3 Board Pack.docx | Page: 3
"Capital expenditure targets were revised upward to £8M following the facilities expansion..."
```

Source markers (`[SOURCE-N]`) are used by `SourceCitationService` to extract citations from the LLM's response.

**Token budget enforcement:** Before returning, the context assembly calculates the total token count of the assembled context, conversation history, system prompt, and user query. If the total exceeds 85% of Qwen3's configured context window, the lowest-ranked chunks are dropped until the budget is satisfied. This is never done silently — the number of dropped chunks is logged and exposed in the response metadata.

---

## Generation Pipeline

### Prompt Assembly

`PromptAssemblyService` constructs a structured prompt with strict section separation:

**System section (immutable — never user-controllable):**
```
You are an enterprise knowledge assistant. Answer questions using ONLY
the provided context sections below. If the context does not contain
sufficient information to answer the question, state that clearly —
do not speculate or use knowledge outside the provided context.
When referencing information, always cite the source using its [SOURCE-N] marker.
```

**Context section:**
The assembled context from Stage 4 of the retrieval pipeline.

**History section:**
The most recent N messages from the conversation, prefixed with role labels.

**User section:**
The original (not rewritten) user query. The rewritten query was used for retrieval; the original is used for generation, as it reflects the user's actual phrasing.

**Why is the system prompt immutable?**
User-controllable system prompts are the primary vector for prompt injection attacks. An attacker who can modify the system prompt can instruct the LLM to ignore grounding, reveal internal context, or produce harmful outputs. Structural separation — passing system content via a different message role than user content — is the primary mitigation.

### LLM Call

The assembled prompt is sent to Qwen3 via `LLMPort`. Responses stream via Server-Sent Events (SSE) to the client. Streaming begins as soon as the first token is available, typically within 500ms of the Ollama call.

After the stream completes, the full response string is available for citation extraction.

### Citation Extraction

`SourceCitationService` scans the LLM response for `[SOURCE-N]` patterns. For each cited source:

1. Maps `SOURCE-N` to the corresponding chunk from the retrieval result
2. Fetches the chunk's document metadata from PostgreSQL
3. Constructs a `Citation` record with chunkId, documentId, page number, section title, and relevance score

Citations are attached to the `Message` aggregate and persisted alongside the conversation. The API response includes a structured `citations` array so the client can render source cards beneath the response.

---

## Cross-Cutting Components

### AuditService

Every state-changing operation logs an `AuditLog` record. The service is called directly by other application services — not via an event bus — to ensure audit records are written in the same transaction as the business operation.

Audit actions follow a `NOUN_VERB` naming convention:

```
DOCUMENT_UPLOADED    DOCUMENT_DELETED    DOCUMENT_INGESTION_FAILED
QUERY_EXECUTED       CONVERSATION_CREATED CONVERSATION_DELETED
USER_CREATED         USER_DEACTIVATED    USER_ROLE_CHANGED
AUTH_LOGIN           AUTH_LOGOUT         AUTH_REFRESH_TOKEN
AUTH_LOGIN_FAILED
```

### ConversationMemoryService

Manages the sliding-window retrieval of conversation history. The window size is configurable (`app.conversation.memory-window-size`, default: 10). Only `USER` and `ASSISTANT` messages are included in the history window passed to the LLM — `SYSTEM` messages are excluded to avoid token waste.

The service does not summarise older history in the initial release. A summarisation strategy (using the LLM to produce a rolling summary of messages beyond the window) is planned for Phase 4.

### MetadataFilterService

Validates and normalises `MetadataFilter` criteria submitted by users. Department and classification values are validated against an allowed list to prevent injection attacks against the Weaviate filter API. Unknown filter keys are rejected with a `400 Bad Request`.

---

## Security Components

### JwtAuthenticationFilter

A `OncePerRequestFilter` that:
1. Extracts the `Bearer` token from the `Authorization` header
2. Validates signature (RS256), expiry, and `jti` (token ID, checked against a revocation list)
3. Resolves the `tid` claim to a `TenantId`
4. Populates the `SecurityContextHolder` with a custom `KaAuthenticationToken` containing UserId, TenantId, and granted authorities

### JwtTokenProvider

Handles access token generation, refresh token generation (random 256-bit secure random, SHA-256 hashed for storage), and token verification. Uses RS256 (asymmetric) — the private key signs tokens, the public key verifies them. Verification services never need the private key.

### SecurityConfig

Configures the Spring Security filter chain. All endpoints require authentication except:
- `POST /api/v1/auth/login`
- `GET /actuator/health`

CSRF protection is disabled (stateless JWT API). Session management is stateless (`SessionCreationPolicy.STATELESS`).

---

## Pluggable Extension Points

The platform is designed with explicit pluggability at every AI touchpoint. Each of the following can be replaced by implementing the corresponding port and registering the new adapter as a Spring `@Component`:

| Extension Point | Port Interface | Default | Upgrade Path |
|---|---|---|---|
| LLM Generation | `LLMPort` | Ollama / Qwen3 | AWS Bedrock, Azure OpenAI, Anthropic |
| Embedding | `EmbeddingPort` | Ollama / nomic-embed-text | OpenAI Ada, Cohere Embed, custom model |
| Vector Store | `VectorStorePort` | Weaviate | pgvector, Pinecone, Qdrant, OpenSearch |
| Re-Ranking | `ReRankingPort` | Noop (pass-through) | Cohere Rerank, cross-encoder, domain-specific |
| Document Parsing | `DocumentParserPort` | Apache Tika | Custom parsers per format |
| File Storage | `FileStoragePort` | Local filesystem | Amazon S3, Azure Blob, Google Cloud Storage |
| Event Publishing | `EventPublisher` | Spring ApplicationEvents | Kafka, RabbitMQ, Amazon EventBridge |
| Chunking | `ChunkingStrategy` | Format-specific strategies | Custom strategies per use case |

---

## Service Interaction Rules

To prevent implicit coupling, these rules govern how services may interact:

1. **Application services call ports** — not other application services directly. If two use cases share logic, extract it into a domain service or a shared application component.

2. **Ingestion services do not know about retrieval** — `DocumentIngestionService` knows nothing about how chunks will be searched. It stores them; the retrieval pipeline uses them.

3. **Generation does not do retrieval** — `GenerationOrchestrationService` receives assembled context as input. It does not call the vector store or PostgreSQL.

4. **Audit is a one-way call** — `AuditService.log()` never returns a result that the calling service uses. If audit logging fails, the exception is caught and logged — it does not fail the business operation.

5. **Re-ranking is always called** — even when the Noop implementation is active. This ensures the re-ranking stage is always present in the pipeline metrics and can be upgraded without changing call sites.

---

## Sequence Diagrams

### Document Ingestion

```
Client → API (POST /api/v1/documents)
  │
  API → DocumentIngestionService.ingest()
    │
    ├─► FileStoragePort.store(bytes)
    │   ← rawContentPath
    │
    ├─► DocumentParserPort.parse(path, format)
    │   ← ParsedDocument(text, extractedMetadata)
    │
    ├─► ChunkingService.chunk(text, format)
    │   ← List<ChunkCandidate>
    │
    ├─► EmbeddingPort.embed(List<String>)   [batched]
    │   ← List<float[]>
    │
    ├─► VectorStorePort.upsert(chunks + vectors)
    │   ← List<vectorId>
    │
    └─► DocumentRepository.save() + ChunkRepository.saveAll()
        (single PostgreSQL transaction)

  API ← 202 Accepted {documentId}
Client ← 202 Accepted
```

### Query with Conversational Memory

```
Client → API (POST /api/v1/query)
  │
  API → QueryOrchestrationService.query()
    │
    ├─► ConversationRepository.findById()
    │   ← conversation history (last N messages)
    │
    ├─► LLMPort.generate(rewritePrompt)    [optional, cached]
    │   ← rewrittenQuery
    │
    ├─► EmbeddingPort.embed(rewrittenQuery)
    │   ← queryVector
    │
    ├─► VectorStorePort.hybridSearch(vector, filter, topK=20)
    │   ← List<ScoredChunk>
    │
    ├─► ReRankingService.rerank(query, candidates)
    │   ← top-5 chunks
    │
    ├─► ContextAssemblyService.assemble(top-5)
    │   ← contextString (token-budget-enforced)
    │
    ├─► PromptAssemblyService.build(context, history, query)
    │   ← Prompt
    │
    ├─► LLMPort.generateStream(prompt)
    │   ← Flux<String>  ──────────────────────────────────────► Client (SSE)
    │
    ├─► SourceCitationService.extract(response, chunks)
    │   ← List<Citation>
    │
    └─► ConversationRepository.save() + QueryRepository.save()
        (single PostgreSQL transaction)
```

### Token Refresh

```
Client → API (POST /api/v1/auth/refresh)
  │
  API → JwtTokenProvider.validateRefreshToken(rawToken)
    ├─► RefreshTokenJpaRepository.findByTokenHash(sha256(rawToken))
    │   ← RefreshTokenEntity (validates active, not expired, not revoked)
    │
    ├─► JwtTokenProvider.generateAccessToken(user)
    │   ← newAccessToken
    │
    ├─► JwtTokenProvider.generateRefreshToken()
    │   ← newRawRefreshToken
    │
    ├─► old RefreshTokenEntity.revoke()  (sets revokedAt)
    │
    └─► RefreshTokenJpaRepository.save(newHashedToken)

  API ← {accessToken, refreshToken}
Client ← 200 OK
```

---

## Database Schema Relationships

```
tenants ──────────────────────────────────────────────────────────┐
    │                                                              │
    ├── users (tenant_id FK)                                       │
    │       └── user_roles (user_id FK, role_id FK)               │
    │       └── refresh_tokens (user_id FK)                       │
    │                                                              │
    ├── documents (tenant_id FK, owner_id→users FK)               │
    │       └── chunks (document_id FK, tenant_id FK)             │
    │                   └── citations (chunk_id FK)               │
    │                                                              │
    ├── conversations (tenant_id FK, user_id FK)                  │
    │       └── messages (conversation_id FK)                     │
    │               └── citations (message_id FK)                 │
    │                                                              │
    ├── queries (tenant_id FK, user_id FK, conversation_id FK)    │
    │                                                              │
    └── audit_logs (tenant_id, user_id — not FK, append-only)    │
                                                                  │
roles (fixed seed data — no tenant FK)  ──────────────────────────┘
```

**Key design notes:**
- `audit_logs` does not have a FK on `user_id` — a deleted user's audit records are retained
- `citations` links `messages` to `chunks` — never to documents directly; the chunk is the atomic unit of attribution
- `refresh_tokens` stores only the SHA-256 hash; the raw token is never persisted

---

## Weaviate Collection Design

```
Collection: DocumentChunk

Properties:
  chunkId          text (not vectorised)   UUID — links to PostgreSQL chunks.id
  documentId       text (not vectorised)   UUID reference
  tenantId         text (not vectorised)   Weaviate tenant key
  content          text (vectorised=true)  The chunk text
  filename         text (not vectorised)
  format           text (not vectorised)   PDF, DOCX, etc.
  department       text (not vectorised)   Metadata filter target
  classification   text (not vectorised)   Metadata filter target
  tags             text[] (not vectorised) Metadata filter target
  pageNumber       int  (not vectorised)
  sectionTitle     text (not vectorised)
  sequenceNumber   int  (not vectorised)
  chunkingStrategy text (not vectorised)

Vectorizer:   text2vec-ollama
  model:       nomic-embed-text
  dimensions:  768
  apiEndpoint: http://ollama:11434

Distance metric: cosine

Inverted index (BM25): enabled on content property only

Multi-tenancy:
  enabled: true
  autoTenantCreation: true

Replication factor: 1 (dev) / 3 (prod)
```

**Hybrid search parameters:**

| Parameter | Value | Rationale |
|---|---|---|
| `alpha` | 0.75 | Vector-weighted; enterprise KB queries are more semantic than keyword-heavy |
| `limit` | 20 | Over-fetch for re-ranking; returning 5 directly degrades precision |
| Pre-filter order | `tenantId` → `department` → `classification` → `tags` | Most-selective first; Weaviate applies filters before ANN to reduce the search space |
| Distance threshold | None (fetch top-20, let re-ranker decide) | A fixed distance threshold silently drops relevant results |
