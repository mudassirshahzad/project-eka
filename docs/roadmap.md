# Roadmap

> Delivery phases, architectural evolution, and the integration path toward MCP, LangGraph, and production-grade Agentic AI for Project EKA (Enterprise Knowledge Assistant).

---

## Table of Contents

- [Delivery Phases](#delivery-phases)
- [Phase 1 — Foundation](#phase-1--foundation)
- [Phase 2 — Ingestion Pipeline](#phase-2--ingestion-pipeline)
- [Phase 3 — Retrieval and Generation](#phase-3--retrieval-and-generation)
- [Phase 4 — Conversational Intelligence](#phase-4--conversational-intelligence)
- [Phase 5 — Enterprise Hardening](#phase-5--enterprise-hardening)
- [Phase 6 — Advanced Retrieval](#phase-6--advanced-retrieval)
- [Phase 7 — MCP Integration](#phase-7--mcp-integration)
- [Phase 8 — LangGraph Integration](#phase-8--langgraph-integration)
- [Phase 9 — Agentic AI Platform](#phase-9--agentic-ai-platform)
- [Architectural Readiness Assessment](#architectural-readiness-assessment)
- [Technology Upgrade Path](#technology-upgrade-path)
- [Decision Log](#decision-log)

---

## Delivery Phases

```
Phase 1   Foundation                     ████████████  ✅ Complete
Phase 2   Ingestion Pipeline             ███░░░░░░░░░  🔄 In Progress
Phase 3   Retrieval & Generation         ░░░░░░░░░░░░  ⏳ Planned
Phase 4   Conversational Intelligence    ░░░░░░░░░░░░  ⏳ Planned
Phase 5   Enterprise Hardening          ░░░░░░░░░░░░  ⏳ Planned
Phase 6   Advanced Retrieval            ░░░░░░░░░░░░  Future
Phase 7   MCP Server                    ░░░░░░░░░░░░  Future
Phase 8   LangGraph Integration         ░░░░░░░░░░░░  Future
Phase 9   Agentic AI Platform           ░░░░░░░░░░░░  Future
```

---

## Phase 1 — Foundation

**Scope:** Everything required before business logic can be written safely.

**Deliverables completed:**

- PostgreSQL schema design with Flyway migrations (V001–V010)
- Hexagonal architecture scaffolding with ArchUnit enforcement
- Domain model — all aggregates, value objects, enums, and port interfaces
- JPA entity layer with `BaseUuidEntity`, `AuditableEntity`, and all 11 entities
- Spring Data JPA repositories for all entities
- Persistence mappers (domain ↔ JPA entity translation)
- Repository adapters implementing domain ports
- `pom.xml` with pinned, compatible dependency versions
- `application.yml` with profile-based configuration
- `HexagonalArchitectureTest` — eight ArchUnit rules

**Architectural decisions locked in Phase 1:**

| Decision | Why It Cannot Change Later |
|---|---|
| Hexagonal architecture | Changing this requires rewriting every service |
| Domain-assigned UUIDs | Database-generated IDs require changing the port contract |
| Tenant-scoped data model | Adding `tenant_id` to an existing schema is a major migration |
| Flyway over JPA DDL | Reverting to DDL auto-generation removes schema safety |
| Append-only audit log | Making audit logs mutable invalidates their compliance value |

---

## Phase 2 — Ingestion Pipeline

**Scope:** Complete document processing from upload to queryable vector embeddings.

**Status:** 🔄 In Progress — Weaviate collection design and adapter underway.

**Deliverables:**

- `DocumentController` with multipart upload, status check, and soft delete endpoints
- `DocumentIngestionService` with full asynchronous pipeline
- Apache Tika integration (`TikaDocumentParser` implementing `DocumentParserPort`)
  - Magic-byte format detection
  - Per-format extraction configuration (page extraction for PDF, slide extraction for PPTX)
- `ChunkingService` with three concrete strategies:
  - `SemanticChunkingStrategy` — sentence-boundary splitting with overlap
  - `SentenceWindowChunkingStrategy` — sliding context window
  - `RowChunkingStrategy` — tabular data with header preservation
- `EmbeddingBatchService` — configurable batch size, exponential back-off, Ollama concurrency control
- `OllamaEmbeddingAdapter` implementing `EmbeddingPort`
- `WeaviateVectorAdapter` implementing `VectorStorePort`
  - Tenant-scoped collection management
  - Batch upsert with Weaviate Java client
- `LocalFileStorageAdapter` implementing `FileStoragePort`
- Ingestion status polling endpoint: `GET /api/v1/documents/{id}`
- Integration tests using Testcontainers (PostgreSQL + Weaviate)

**Phase 2 success criteria:**

- A PDF can be uploaded via the REST API and reaches status `INDEXED` without manual intervention
- Chunks are present in both PostgreSQL and Weaviate after ingestion
- Ingestion of a 100-page PDF completes within 120 seconds on a CPU-only Ollama instance
- Ingestion failures produce a clear error message on the document record

---

## Phase 3 — Retrieval and Generation

**Scope:** End-to-end question answering with source citations.

**Planned deliverables:**

- `QueryController` with synchronous and streaming query endpoints
- `QueryOrchestrationService` wiring the full retrieval pipeline
- `QueryRewritingService` with prompt template and result caching
- `SemanticSearchService` — Weaviate ANN query with metadata pre-filter
- `KeywordSearchService` — PostgreSQL FTS with `plainto_tsquery`
- `HybridRetrievalService` — result fusion via Reciprocal Rank Fusion
- `NoopReRankingService` — pass-through implementation of `ReRankingPort`
- `ContextAssemblyService` — token-budget-aware context building with source markers
- `PromptAssemblyService` — structured prompt with isolated system section
- `OllamaLLMAdapter` implementing `LLMPort`
  - Blocking generation for synchronous responses
  - Streaming via `Flux<String>` for SSE
- `SourceCitationService` — marker extraction and chunk metadata resolution
- `GenerationOrchestrationService` — assembles and executes the generation call
- SSE streaming endpoint with citation payload appended after stream completion
- `QueryEntity` persistence — every query execution is recorded for analytics

**Phase 3 success criteria:**

- A natural language question returns a streamed response grounded in indexed documents
- Citations appear in the response referencing the correct source documents
- Responses do not contain information outside the indexed knowledge base (hallucination control)
- End-to-end latency (query receipt to first token) is under 2 seconds on a GPU Ollama instance

---

## Phase 4 — Conversational Intelligence

**Scope:** Stateful conversations with memory, context-aware query rewriting, and multi-turn coherence.

**Planned deliverables:**

- `ConversationController` — create, list, get, delete conversation endpoints
- `ConversationService` — conversation lifecycle management
- `ConversationMemoryService` — sliding-window history with configurable size
- Full integration of conversation history into query rewriting
- Full integration of conversation history into generation prompt assembly
- Conversation title auto-generation (LLM summarises the first exchange)
- Message pagination for long conversations: `GET /api/v1/conversations/{id}/messages`
- Conversation soft-delete with data retention
- `GET /api/v1/conversations` — list user's conversations with last-message preview

**Planned — Phase 4 stretch goals:**

- **Conversation summarisation** — when the conversation exceeds the memory window, the LLM produces a rolling summary of older messages. The summary replaces the dropped messages in the history prompt, preserving context beyond the window size.
- **Topic detection** — classify each conversation's subject for analytics and auto-titling

**Phase 4 success criteria:**

- A user can ask "what did we discuss earlier about the budget?" and receive a correct contextual answer
- Conversation history is correctly bounded by the memory window
- Multi-turn conversations remain coherent over 20+ exchanges

---

## Phase 5 — Enterprise Hardening

**Scope:** Production readiness — security, observability, operational controls.

**Planned deliverables:**

- `AuthController` — login, logout, token refresh, token revocation
- `JwtTokenProvider` with RS256 (asymmetric key pair)
- `JwtAuthenticationFilter` integrated into Spring Security filter chain
- Refresh token rotation with revocation on logout and re-use detection
- `AdminController` — user management, audit log viewing, system metrics
- Rate limiting — per-user, per-tenant limits on ingestion and query endpoints
- Prompt injection detection — pattern matching and heuristic scoring on user input
- Structured JSON logging with correlation IDs propagated through all log lines
- Micrometer custom metrics for ingestion pipeline and retrieval pipeline
- Prometheus scrape endpoint, Grafana dashboard definitions
- OpenTelemetry trace propagation through ingestion and retrieval flows
- Weaviate schema initialisation on first startup (`WeaviateCollectionInitialiser`)
- Scheduled reconciliation job — detect and repair PostgreSQL ↔ Weaviate drift
- Scheduled refresh token cleanup — delete expired tokens older than 30 days
- Testcontainers integration tests for the full pipeline
- Docker Compose production configuration with health checks and resource limits

**Phase 5 success criteria:**

- Unauthorised requests receive `401 Unauthorized` at the filter chain level
- A `VIEWER` user cannot upload or delete documents
- Audit log entries appear for every state-changing operation
- The Grafana dashboard shows ingestion throughput, embedding latency, query latency, and error rate
- The platform handles 100 concurrent query requests without degrading response time by more than 20%

---

## Phase 6 — Advanced Retrieval

**Scope:** Retrieval quality improvements that lift answer accuracy without changing the architectural model.

**Planned deliverables:**

- **Re-ranking implementation** — integrate a cross-encoder model:
  - Deploy a local cross-encoder via Ollama or a Python FastAPI sidecar
  - Implement `CrossEncoderReRankingAdapter` implementing `ReRankingPort`
  - A/B test against the Noop implementation on a query quality benchmark
- **Cohere Rerank adapter** — `CohereReRankingAdapter` for teams with cloud API access
- **Hypothetical Document Embedding (HyDE)** — generate a hypothetical answer to the query, embed it, and search with the hypothetical answer's vector. This improves recall for factual questions where the query and its answer are worded very differently
- **Metadata filter UI** — allow users to scope queries to specific departments or classifications
- **Document access control** — per-document sharing permissions (document-level RBAC beyond role-level)
- **Duplicate detection** — content hash check on upload to prevent duplicate ingestion
- **Incremental re-ingestion** — detect changed documents and re-embed only the changed sections

**Phase 6 success criteria:**

- Re-ranking measurably improves answer relevance score on the internal evaluation set
- Metadata filtering correctly restricts results to the requested department or classification
- Duplicate upload is rejected with a clear error message

---

## Phase 7 — MCP Integration

**Scope:** Expose the knowledge base as MCP tools, making it accessible to Claude Desktop, external agents, and any MCP-compatible client.

### What is MCP?

The Model Context Protocol is an open standard for connecting AI systems to external data sources and tools. An MCP server exposes capabilities as `tools` (functions an LLM can call) and `resources` (data an LLM can read). Any MCP-compatible client — Claude Desktop, Claude API tool calls, or a custom agent — can discover and invoke these tools.

### Why MCP readiness is already built in

The platform's existing use case interfaces are the MCP tool contracts. Adding an MCP server requires zero changes to the domain, application, or infrastructure layers:

```
MCP Client
    │ MCP Protocol (JSON-RPC 2.0)
    ▼
MCPServerAdapter (new infrastructure adapter)
    │ Translates MCP tool calls to use case calls
    ▼
QueryOrchestrationUseCase.query()
DocumentIngestionUseCase.ingest()
ConversationUseCase.*()
    │
    ▼
[existing application, domain, infrastructure — unchanged]
```

### Planned MCP tools

| Tool | Input | Output | Mapped Use Case |
|---|---|---|---|
| `search_knowledge_base` | query, filters?, topK? | chunks[], citations[] | `QueryOrchestrationUseCase.query()` |
| `upload_document` | content, format, metadata | documentId, status | `DocumentIngestionUseCase.ingest()` |
| `get_document_status` | documentId | status, chunkCount, error? | `DocumentRepository.findById()` |
| `list_documents` | department?, tags?, page? | documents[] | `DocumentRepository.findByTenantId()` |
| `delete_document` | documentId | success | `DocumentRepository.softDelete()` |
| `get_conversation` | conversationId | messages[], citations[] | `ConversationUseCase.getConversation()` |

### Planned MCP resources

| Resource URI | Content | Purpose |
|---|---|---|
| `document://{id}` | Document metadata and status | Agent-readable document record |
| `conversation://{id}` | Full conversation history | Agent-readable conversation context |
| `chunk://{vectorId}` | Chunk content and metadata | Agent-readable source chunk |

### Planned deliverables

- Spring MCP Server dependency integration
- `MCPController` / `MCPServerAdapter` translating JSON-RPC tool calls to use case calls
- Tool and resource schema definitions (OpenAPI-style JSON Schema)
- MCP authentication — API key or JWT delegation from the MCP client
- Tenant context propagation for MCP calls (MCP client passes tenant claim)
- End-to-end test: Claude Desktop querying the knowledge base via MCP

### Architectural note

MCP is transport — it is functionally identical to the HTTP REST API at the adapter level. The same hexagonal principle applies: MCP adapter calls use cases; use cases call ports; ports call infrastructure. No business logic lives in the MCP adapter.

---

## Phase 8 — LangGraph Integration

**Scope:** Replace the linear retrieval pipeline with a graph-based orchestration model, enabling conditional routing, parallel execution, and iterative refinement.

### What is LangGraph?

LangGraph (from LangChain) is a framework for building stateful, multi-step AI workflows as directed graphs. Each node in the graph is a function; edges are conditional transitions. The graph holds shared state across all nodes. This model enables patterns impossible in a linear pipeline:

- **Iterative retrieval** — if the first retrieval is insufficient, the graph loops back to rewrite and re-retrieve
- **Parallel search** — semantic and keyword searches run as parallel graph branches and merge at a join node
- **Quality gates** — a grader node evaluates whether retrieved chunks are relevant before generation
- **Self-correction** — if the generated response does not cite enough sources, the graph routes back to retrieve more

### Why the current design is LangGraph-ready

Every service in the current application layer is stateless. The `KnowledgeQuery` aggregate holds the accumulated state of a retrieval operation. This design maps directly to LangGraph's state model:

| Current Service | LangGraph Node | State Transition |
|---|---|---|
| `QueryRewritingService` | `rewrite_query` | `state.original → state.rewritten` |
| `SemanticSearchService` | `semantic_search` | `state.rewritten → state.semantic_candidates` |
| `KeywordSearchService` | `keyword_search` | `state.rewritten → state.keyword_candidates` |
| `HybridRetrievalService` | `fuse_results` | `state.candidates → state.fused_candidates` |
| `ReRankingService` | `rerank` | `state.fused → state.ranked` |
| `ContextAssemblyService` | `assemble_context` | `state.ranked → state.context` |
| `GenerationOrchestrationService` | `generate` | `state.context → state.response` |
| `SourceCitationService` | `extract_citations` | `state.response → state.citations` |

Adding LangGraph orchestration requires:
1. A `LangGraphQueryGraphFactory` that wires the existing services as graph nodes
2. A `GraphExecutionAdapter` that drives the graph and persists intermediate state
3. Zero changes to the existing services themselves

### Planned graph topology

```
[start]
   │
   ▼
[rewrite_query]
   │
   ├──────────────────────────────────┐
   ▼                                  ▼
[semantic_search]            [keyword_search]
   │                                  │
   └──────────────┬───────────────────┘
                  ▼
          [fuse_results]
                  │
                  ▼
           [relevance_check]   ←─── conditional edge
          /            \
    sufficient       insufficient
         │               │
         │          [rewrite_query]  (loop, max 2 iterations)
         ▼
      [rerank]
         │
         ▼
  [assemble_context]
         │
         ▼
     [generate]
         │
         ▼
  [extract_citations]
         │
         ▼
   [quality_check]   ←─── conditional edge
  /             \
adequate       inadequate
    │              │
    ▼        [retrieve_more]  (targeted retrieval for gaps)
 [end]
```

### Graph state model

```
GraphState:
  tenantId: TenantId
  userId: UserId
  conversationId: ConversationId?
  originalQuery: String
  rewrittenQuery: String?
  filter: MetadataFilter
  semanticCandidates: List<ScoredChunk>
  keywordCandidates: List<ScoredChunk>
  fusedCandidates: List<ScoredChunk>
  rankedCandidates: List<ScoredChunk>
  context: String?
  response: String?
  citations: List<Citation>
  iterationCount: int
  qualityScore: float?
```

### Planned deliverables

- `spring-ai-graph` or `langgraph4j` dependency integration
- Graph node implementations wrapping existing services
- `RelevanceGraderService` — LLM-based relevance scoring for retrieved chunks
- `QualityCheckerService` — validates response groundedness before returning
- Configurable graph topology (full graph, or simplified linear fallback)
- Graph execution metrics — per-node latency, iteration counts, quality scores

---

## Phase 9 — Agentic AI Platform

**Scope:** Evolve from a retrieval platform into an orchestrated multi-agent system capable of complex, multi-step knowledge tasks.

### The agent model

A multi-agent system distributes complex tasks across specialised agents. Each agent has a specific capability set and communicates with others through well-defined interfaces. The EKA platform naturally decomposes into:

| Agent | Capability | Foundation |
|---|---|---|
| **Orchestrator Agent** | Decomposes complex queries, routes to sub-agents, synthesises results | LangGraph graph driving the other agents |
| **Retrieval Agent** | Semantic + keyword search, re-ranking, context assembly | Current RAG pipeline (Phase 3–6) |
| **Ingestion Agent** | Document processing, format detection, chunking, embedding | Current ingestion pipeline (Phase 2) |
| **Synthesis Agent** | Cross-document synthesis, comparison, summarisation | New generation variant with multi-document context |
| **Analytics Agent** | Query trend analysis, knowledge gap detection, usage reporting | New analytical pipeline over `queries` table |
| **Citation Agent** | Source verification, claim-level citation, fact-checking | New specialised pipeline using chunk content + metadata |

### Why the current design supports multi-agent evolution

**Services are stateless.** An agent framework manages state; services provide functions. This is the critical design requirement — stateful services cannot be safely shared across concurrent agent invocations.

**Port interfaces are stable tool contracts.** `VectorStorePort.hybridSearch()`, `LLMPort.generate()`, and `EmbeddingPort.embed()` are already suitable as tool definitions for agent frameworks without modification.

**Domain events support agent coordination.** The `DocumentIngested`, `QueryExecuted`, and `ConversationCreated` events can drive agent workflows. An agent graph node subscribes to a domain event and triggers the next stage.

**Multi-tenancy is embedded in every call.** Each agent invocation carries tenant context through the call chain without relying on thread-local state — essential for safe concurrent multi-agent operation.

### Integration with external AI systems via MCP

Once the MCP server (Phase 7) is live, external agents — including Claude models accessed via the Anthropic API — can invoke the platform's knowledge base as a tool. This creates the following ecosystem:

```
External Claude Agent (Anthropic API)
    │
    │ MCP tool calls
    ▼
EKA MCP Server (Phase 7)
    │
    ▼
EKA RAG Pipeline
    │
    ▼
Weaviate + PostgreSQL + Ollama
```

The EKA platform becomes a knowledge tool for any agent in the broader AI ecosystem, not just a standalone application.

### Long-term vision: The Knowledge Mesh

In the fully evolved state, the EKA platform participates in a knowledge mesh — a network of specialised knowledge agents and tools:

```
User Request
    │
    ▼
Orchestrator Agent
    │
    ├── EKA Retrieval Agent (internal documents)
    ├── Web Search Agent (external information)
    ├── Database Agent (structured data queries)
    ├── Code Analysis Agent (codebase queries)
    └── Synthesis Agent (combines all results)
         │
         ▼
Grounded, multi-source response with citations
```

Every component in this mesh is independently replaceable. The hexagonal architecture and MCP standard make each piece interoperable without tight coupling.

---

## Architectural Readiness Assessment

This table evaluates how prepared the current Phase 1 codebase is for each future phase:

| Future Capability | Readiness | What Enables It | What's Missing |
|---|---|---|---|
| New LLM provider | High | `LLMPort` interface | New adapter class only |
| New embedding model | High | `EmbeddingPort` interface | New adapter class only |
| pgvector instead of Weaviate | High | `VectorStorePort` interface | New adapter class only |
| Cross-encoder re-ranking | High | `ReRankingPort` interface | New adapter class only |
| MCP server | High | Use case interfaces are tool contracts | `MCPServerAdapter` class |
| LangGraph node wrapping | High | Services are stateless | Graph factory + node wrappers |
| Multi-agent orchestration | Medium | Stateless services, domain events | Agent framework, state management |
| Cloud LLM (Bedrock, Azure OpenAI) | High | `LLMPort` interface | Cloud provider adapter |
| Amazon S3 file storage | High | `FileStoragePort` interface | S3 adapter class |
| Kafka event streaming | Medium | `EventPublisher` port | Kafka producer adapter |
| Microservice extraction | Medium | Bounded context package structure | Service mesh, API contracts |

---

## Technology Upgrade Path

### Near-term upgrades (Phases 2–5)

| Component | Current | Upgrade Trigger |
|---|---|---|
| `nomic-embed-text` | 768-dim | Larger model available (1536-dim) with measurable quality lift |
| Qwen3 | Latest | New model with better reasoning or longer context window |
| Weaviate | 1.25 | Security patch or new multi-tenancy feature |
| Spring Boot | 3.5 | LTS update or critical security fix |

### Medium-term upgrades (Phases 6–8)

| Component | Upgrade Path | Decision Criteria |
|---|---|---|
| Re-ranking | Noop → local cross-encoder → Cohere Rerank | Measured improvement on evaluation set |
| File storage | Local → Amazon S3 | Multi-instance deployment requirement |
| Event bus | Spring ApplicationEvents → Kafka | Need for async processing or replay |
| LLM | Ollama → AWS Bedrock (Claude models) | Organisation compliance or scale requirement |

### Long-term platform decisions (Phase 9)

| Decision | Options | Current Recommendation |
|---|---|---|
| Agent framework | LangGraph4j, Spring AI Agents, custom | Evaluate LangGraph4j at Phase 8; Spring AI Agents if Spring AI support matures |
| MCP transport | HTTP (SSE), stdio, WebSocket | HTTP for enterprise network traversal |
| Service extraction | Keep monolith or extract Ingestion service | Extract only if the ingestion team is distinct and independent deployment is required |

---

## Decision Log

This log records the significant architectural decisions made during Phase 1 and their rationale. It is a living document.

| Date | Decision | Alternatives Considered | Rationale |
|---|---|---|---|
| 2026-06 | Modular Monolith over Microservices | Full microservices from day one | Operational simplicity; extract only when boundary demands it |
| 2026-06 | Hexagonal Architecture with ArchUnit enforcement | Layered architecture | Provider independence for AI components; testability |
| 2026-06 | PostgreSQL for FTS (not Elasticsearch) | Elasticsearch, OpenSearch | Reduces operational dependencies; BM25 via `tsvector` is sufficient at current scale |
| 2026-06 | Weaviate native multi-tenancy | Property-based filtering | Physical data isolation; performance at multi-tenant scale |
| 2026-06 | JWT RS256 (not HS256) | HS256 shared secret | Asymmetric keys allow verification without exposing signing key |
| 2026-06 | Domain-assigned UUIDs | Database-generated IDs | Domain identity independence from persistence technology |
| 2026-06 | Domain-owned `PageRequest`/`PageResult` | Spring `Pageable` in domain | No Spring imports in domain layer (ArchUnit-enforced) |
| 2026-06 | Append-only `audit_logs` with PostgreSQL RULE | Application-level enforcement | Database-level immutability cannot be bypassed by application bugs |
| 2026-06 | BIGSERIAL PK on `audit_logs` | UUID PK | Sequential PK for optimal range scan performance on high-volume append-only table |
| 2026-06 | Noop re-ranking as default | Ship without re-ranking | Pipeline completeness without delivery risk; upgrade path is clear |
| 2026-06 | Apache Tika for parsing | Format-specific parsers | Single dependency covering all 8 formats; magic-byte detection |
