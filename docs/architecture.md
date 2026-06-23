# Architecture Overview

> Project EKA (Enterprise Knowledge Assistant) — High-Level Architecture, Design Principles, and Technology Decisions

---

## Table of Contents

- [Architectural Philosophy](#architectural-philosophy)
- [High-Level Architecture](#high-level-architecture)
- [Architectural Style](#architectural-style)
- [Technology Decisions](#technology-decisions)
- [Cross-Cutting Concerns](#cross-cutting-concerns)
- [Deployment Architecture](#deployment-architecture)
- [Future Architecture Evolution](#future-architecture-evolution)
- [Architectural Challenges and Mitigations](#architectural-challenges-and-mitigations)

---

## Architectural Philosophy

The platform is designed around three governing principles that inform every decision:

**1. Provider Independence**
Every AI integration — LLM generation, embedding, vector storage, document parsing — is accessed exclusively through a port interface in the domain layer. The application layer never imports from `org.springframework.ai`, `io.weaviate`, or `org.apache.tika` directly. This means swapping Ollama for AWS Bedrock, or Weaviate for pgvector, requires changing only the infrastructure adapter. The domain, application, and API layers are unchanged.

**2. Modular Monolith First**
The platform is a single deployable unit with strict internal module boundaries. Microservices are not the starting point — they are an extraction option when team ownership, independent scaling, or deployment isolation genuinely demands it. Premature service decomposition is the most common source of operational complexity and latency on internal enterprise platforms. The codebase is structured so that a bounded context can be extracted to a service when the time is right, without the day-one cost of distributed systems.

**3. Hexagonal Architecture (Ports & Adapters)**
The domain layer is pure Java with zero framework dependencies. It is completely testable without a running database, LLM, or web server. All external concerns — persistence, AI, HTTP, file storage — are adapters that implement port interfaces defined in the domain. ArchUnit tests enforce this at build time; any violation fails the CI pipeline.

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     ENTERPRISE KNOWLEDGE ASSISTANT                           │
│                                                                             │
│  ┌───────────────┐   ┌───────────────┐   ┌───────────────┐                 │
│  │   Web / SPA   │   │  API Clients  │   │   Admin UI    │                 │
│  └───────┬───────┘   └───────┬───────┘   └───────┬───────┘                 │
│          └───────────────────┴───────────────────-┘                         │
│                              │                                               │
│             ┌────────────────▼──────────────────┐                          │
│             │   API Gateway (Nginx / Traefik)    │                          │
│             │   TLS termination · Rate limiting  │                          │
│             └────────────────┬──────────────────┘                          │
│                              │                                               │
│             ┌────────────────▼──────────────────┐                          │
│             │  Spring Security + JWT Filter      │                          │
│             │  Tenant resolution · RBAC          │                          │
│             └──────┬──────────────┬──────────────┘                          │
│                    │              │                                          │
│          ┌─────────▼──┐    ┌──────▼──────┐   ┌──────────────┐              │
│          │  Ingestion  │    │  Retrieval  │   │  Conversation│              │
│          │  Pipeline   │    │  Pipeline   │   │  Pipeline    │              │
│          └─────────┬───┘    └──────┬──────┘   └──────┬───────┘              │
│                    │               │                  │                      │
│          ┌─────────▼───────────────▼──────────────────▼──────────┐         │
│          │                   Domain Layer                          │         │
│          │   Document · Chunk · Conversation · Query · User        │         │
│          └───────────┬────────────────────────────────────────────┘         │
│                      │                                                       │
│          ┌───────────▼─────────────────────────────────────────┐            │
│          │                Infrastructure Layer                   │            │
│          │                                                       │            │
│  ┌───────────────┐  ┌───────────────┐  ┌──────────────────┐    │            │
│  │  PostgreSQL   │  │   Weaviate    │  │  Ollama (LLM +   │    │            │
│  │  Adapter      │  │   Adapter     │  │  Embedding)      │    │            │
│  └───────────────┘  └───────────────┘  └──────────────────┘    │            │
│          └─────────────────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Architectural Style

### Hexagonal Architecture (Ports & Adapters)

The dependency rule is absolute and machine-enforced:

```
Domain ← Application ← API
Domain ← Infrastructure
```

The domain layer cannot import from any other layer. The application layer cannot import from infrastructure or API. This produces a codebase where:

- The domain can be tested without any external systems
- The application logic can be tested by mocking ports without real databases or LLMs
- Infrastructure adapters can be swapped without affecting business logic
- Any future MCP adapter, LangGraph integration, or multi-agent orchestrator is just another adapter

**ArchUnit enforcement** — eight architectural rules are checked on every test run. A violation is a build failure:

| Rule | Purpose |
|---|---|
| Domain must not depend on application | Domain stays technology-neutral |
| Domain must not depend on infrastructure | Domain stays technology-neutral |
| Domain must not depend on API | Domain stays technology-neutral |
| Application must not depend on infrastructure | Use cases stay testable |
| Application must not depend on API | No circular dependencies |
| Infrastructure must not depend on API | Clear separation of concerns |
| JPA annotations must not appear in domain | No ORM leak |
| Spring Framework must not appear in domain | No framework lock-in |

### Modular Monolith Boundaries

The monolith is divided into bounded contexts by package structure. Module boundaries are enforced by the hexagonal rules above. The four current bounded contexts are:

| Bounded Context | Responsibility |
|---|---|
| **Document** | File upload, format detection, ingestion state machine |
| **Chunk** | Chunking strategies, vector ID management |
| **Conversation** | Session management, message history, citations |
| **Query** | Retrieval orchestration, rewriting, analytics |

Each bounded context communicates with others through domain events or shared value objects — never through direct service-to-service calls within the application layer.

---

## Technology Decisions

### Java 21

Java 21 virtual threads (Project Loom) allow the ingestion pipeline — which is I/O-bound, waiting on Tika, Ollama, and Weaviate — to run with simple blocking code on lightweight threads, without the complexity of reactive programming. The ingestion executor uses a conventional `ThreadPoolTaskExecutor` whose threads are virtual under the JVM. This eliminates callback hell and makes stack traces readable.

**Trade-off:** Preview features (structured concurrency) are enabled but not used in the initial phase. The team should evaluate structured concurrency for the ingestion pipeline in Phase 2.

### Spring Boot 3.5 + Spring AI 1.0.0

Spring AI provides a unified abstraction over LLMs, embedding models, and vector stores. A single `ChatClient` interface works for Ollama locally and any cloud LLM in production. The `VectorStore` abstraction covers Weaviate, pgvector, Pinecone, and others — the same application code.

**Trade-off:** Spring AI 1.0.0 is the first stable release. Some APIs stabilised late. The platform pins the BOM and tests against it. API churn from 0.x versions is no longer a concern.

### PostgreSQL 16 as Primary Store

PostgreSQL serves multiple roles: relational metadata store, FTS engine (BM25 via `tsvector`/`tsquery`), JSONB document store for query filters and audit details, and conversation memory. Keeping these concerns in one database reduces operational complexity significantly compared to adding Elasticsearch for keyword search.

**Trade-off:** At very high chunk volumes (100M+), the FTS index on `chunks.content` becomes the limiting factor. At that scale, offloading keyword search to OpenSearch is the right move. The hybrid retrieval port abstraction makes this a one-adapter change.

### Weaviate for Vector Storage

Weaviate is chosen over pgvector for three reasons:

1. **Native multi-tenancy** — physical data isolation per tenant without application-level partitioning hacks
2. **Hybrid search built-in** — Weaviate's alpha parameter blends BM25 and ANN in a single query; duplicating this in PostgreSQL requires more plumbing
3. **Scale independence** — Weaviate's sharding and replication model scales horizontally independent of the application tier

**Trade-off:** Weaviate introduces an additional operational dependency. In single-tenant or low-scale deployments, pgvector with the Spring AI pgvector adapter is a simpler choice. The `VectorStorePort` interface makes this a one-line configuration change.

### Ollama + Qwen3 + nomic-embed-text

All AI inference runs locally. No data leaves the network. Ollama provides a single API surface for model management, context configuration, and streaming. Qwen3 is selected for its strong multilingual reasoning and instruction-following capability. nomic-embed-text provides competitive 768-dim embeddings at low latency.

**Trade-off:** Local inference is slower than cloud APIs. A GPU-equipped host (NVIDIA, 16 GB VRAM recommended) is required for acceptable generation latency. The `LLMPort` and `EmbeddingPort` interfaces abstract this completely — production deployments can use AWS Bedrock or Azure OpenAI with zero application changes.

### Flyway for Schema Management

Schema changes are versioned, checksum-validated migrations. There is no `spring.jpa.hibernate.ddl-auto=create` anywhere in the codebase. Flyway validates the schema on startup — a schema drift between code and database is a startup failure, not a silent runtime bug.

**Trade-off:** Flyway requires discipline. Every schema change must be a new migration file. Modifying an existing applied migration breaks the checksum. The team must treat migration files as immutable once applied to any environment.

---

## Cross-Cutting Concerns

### Multi-Tenancy

Tenant isolation is enforced at three levels:

1. **Application level** — every service method that accesses data requires a `TenantId`, resolved from the JWT `tid` claim by the security filter before any business logic runs
2. **Database level** — every query-facing table has `tenant_id`. PostgreSQL Row Level Security (RLS) policies enforce this at the database level as a defence-in-depth measure
3. **Vector store level** — Weaviate's native multi-tenancy provides physical data separation; tenant data is stored in separate physical shards

### Observability

| Signal | Tool | Integration |
|---|---|---|
| Metrics | Micrometer → Prometheus → Grafana | Auto-instrumented via Spring Boot Actuator |
| Logs | Structured JSON → Loki | `logback-spring.xml` with JSON encoder |
| Traces | Micrometer Tracing → Jaeger / Tempo | `spring-boot-starter-actuator` + OTEL exporter |
| Health | Spring Actuator `/actuator/health` | Composite health checks for DB, Weaviate, Ollama |

Custom metrics instrument the ingestion pipeline (chunk throughput, embedding latency, vector store write latency) and the retrieval pipeline (query rewrite latency, search latency, re-rank latency, total pipeline latency).

### Audit Logging

Every user-visible action produces an `AuditLog` record written to `audit_logs`. The table is protected by PostgreSQL `RULE` statements that silently discard any `UPDATE` or `DELETE` — even from the application's own database user. The `AUDITOR` role can read audit logs but cannot modify them. Audit log entries capture:

- Tenant, user, IP address
- Action (e.g., `DOCUMENT_UPLOADED`, `QUERY_EXECUTED`, `USER_CREATED`)
- Resource type and ID
- Structured detail payload (JSONB)
- Timestamp (immutable)

### Async Processing

Document ingestion is the most resource-intensive operation. It runs on a dedicated `ingestionExecutor` thread pool separate from the HTTP thread pool. This prevents a large batch upload from starving query-serving threads. The ingestion service returns `202 Accepted` immediately and processes asynchronously, with status queryable via `GET /api/v1/documents/{id}`.

---

## Deployment Architecture

### Production Topology

```
Internet
    │
    ▼
Nginx / Traefik          ← TLS 1.3, HSTS, rate limiting, CSP headers
    │
    ▼
Ka API × N instances     ← Stateless Spring Boot; horizontal scale freely
    │
    ├── PostgreSQL Primary + Read Replica
    │       └── Flyway migrations on primary only
    │
    ├── Weaviate Cluster (3 nodes, replication factor 3)
    │       └── Backup: nightly volume snapshot
    │
    └── Ollama (GPU host)
            └── Qwen3 + nomic-embed-text pre-loaded
```

### Scaling Characteristics

| Component | Scaling Model | Bottleneck |
|---|---|---|
| API servers | Horizontal (stateless) | None — add instances freely |
| Ingestion workers | Horizontal via async executor | Ollama embedding throughput |
| Ollama | Vertical (GPU memory) | VRAM — one model per GPU context |
| PostgreSQL | Vertical + read replicas | Connection pool (HikariCP, max 20 per instance) |
| Weaviate | Horizontal sharding | Network I/O at very high dimension counts |

### Docker Compose Layout

The local development stack runs as a single Docker Compose file with five services:

| Service | Image | Port | Purpose |
|---|---|---|---|
| `nginx` | `nginx:alpine` | 80, 443 | Reverse proxy and TLS |
| `ka-api` | `ka-api:latest` | 8080 (internal) | Spring Boot application |
| `postgres` | `postgres:16-alpine` | 5432 (internal) | Relational data |
| `weaviate` | `semitechnologies/weaviate:1.25` | 8081 (internal) | Vector store |
| `ollama` | `ollama/ollama:latest` | 11434 (internal) | LLM and embedding |

Secrets are never in the Compose file. They are injected as environment variables from a `.env` file (excluded from version control) or a secrets manager in production.

---

## Future Architecture Evolution

### MCP Server Integration

The Model Context Protocol server is an adapter, nothing more. The existing use case interfaces (`QueryOrchestrationUseCase`, `DocumentIngestionUseCase`) are the MCP tool contracts. Adding an MCP server requires:

1. A new `MCPController` that translates MCP JSON-RPC calls to use case calls
2. Tool definitions that mirror the existing use case method signatures
3. Zero changes to domain, application, or any existing infrastructure

The platform will be discoverable by Claude Desktop, external agents, and any MCP-compatible client.

### LangGraph / Agent Orchestration

The domain service design is deliberately stateless. State lives in `Conversation`, `KnowledgeQuery`, and the underlying persistence. This is the prerequisite for graph-based orchestration: the graph holds state, and each node calls into a stateless service.

When LangGraph integration is added, every existing service becomes a graph node with no changes required:

- `QueryRewritingService` → `rewrite` node
- `HybridRetrievalService` → `retrieve` node
- `ReRankingService` → `rank` node
- `GenerationOrchestrationService` → `generate` node

Domain events already flow between these stages — they can drive graph state transitions directly.

---

## Architectural Challenges and Mitigations

| Challenge | Risk | Mitigation |
|---|---|---|
| PostgreSQL + Weaviate sync | Vectors and metadata can drift (crash between writes) | `vector_id` FK is the reconciliation key; a scheduled reconciliation job detects and repairs orphans |
| Ollama cold start | First query after model load takes 30–90 seconds | Readiness probe waits for `/api/tags` to return required models; API gateway returns 503 until ready |
| Prompt injection | User query text flows into the LLM prompt | System prompt is immutable and structurally separated from user content; prompt injection detection layer planned for Phase 5 |
| Memory window truncation | Silently truncating conversation history changes context semantics | Token counting before prompt assembly; summarisation strategy for conversations exceeding the window planned for Phase 4 |
| Large file ingestion | Tika can exhaust JVM heap on large PDFs or complex XLSX | File size limits per format enforced at upload; Tika runs in the dedicated `ingestionExecutor` pool with configurable JVM heap |
| Chunk content growth | `chunks.content` FTS index grows proportionally to ingested data | Partitioning `chunks` by `tenant_id` or `created_at` range; monitoring index size via Prometheus |
