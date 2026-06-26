# Project EKA — Enterprise Knowledge Assistant

> An open architecture exploration of how modern AI can transform scattered enterprise knowledge into a secure, searchable and conversational platform — built with Java 21, Spring Boot 3.5, Spring AI, Weaviate and Ollama, with complete data ownership and on-premises deployment.

---

## Why Project EKA?

Most enterprise knowledge is trapped inside documents, presentations, spreadsheets, emails and internal systems.

Project EKA explores how modern AI architectures can transform scattered enterprise knowledge into a secure, searchable and conversational knowledge platform while keeping full control of data ownership and deployment.

---

## Project Vision

Project EKA is designed from day one to support the full spectrum of modern enterprise AI — from basic document retrieval all the way to autonomous multi-agent workflows.

| Vision | What it means |
|---|---|
| **Enterprise RAG** | Transforms a document library into a conversational knowledge base. Users ask questions in natural language; the platform retrieves grounded, cited answers from indexed enterprise content. |
| **MCP Readiness** | The platform exposes its knowledge base as Model Context Protocol (MCP) tools. Any MCP-compatible client — Claude Desktop, external agents, or custom AI systems — can query enterprise knowledge as a first-class tool without any domain changes. |
| **LangGraph Readiness** | Every service is stateless by design. The retrieval pipeline maps directly to a LangGraph graph where each existing service becomes a node — enabling iterative retrieval, relevance grading and self-correction with no rewrites required. |
| **Agent-to-Agent Readiness** | Domain events and stateless service design support multi-agent coordination. Specialised agents (retrieval, ingestion, synthesis, analytics) can work together without tight coupling. |
| **Provider Independence** | Every AI touchpoint — LLM generation, embeddings, vector storage, document parsing — sits behind a port interface. Swap Ollama for AWS Bedrock, or Weaviate for pgvector, by changing one adapter. No business logic changes. |

---

## Current Status

**Active Phase: P02.2 — Business Use Cases** 🚧 In Progress

P01 (Infrastructure Completion) and P02.1 (Application Layer Foundation) are complete. P02.2 is now in progress.

---

## Progress Tracker

### P01 — Infrastructure Completion ✅

- [x] `ChatSessionPersistenceMapper` (bidirectional, follows ChunkPersistenceMapper pattern)
- [x] `ChatSessionRepositoryAdapter` (implements `ChatSessionRepository`, upsert pattern)
- [x] `DocumentTagPersistenceMapper` (bidirectional)
- [x] `DocumentTagRepositoryAdapter` (implements `DocumentTagRepository`)
- [x] `ConversationPersistenceMapper` — `messageToEntity()` overloaded with `sessionId` parameter
- [x] `AppProperties` `@ConfigurationProperties` for `app.*` (ingestion, retrieval, conversation, storage)
- [x] `WeaviateVectorStoreAdapter` — replaced `@Value` with `AppProperties` injection
- [x] Fixed Spring AI 1.0.0 artifact IDs (`spring-ai-starter-model-ollama`, `spring-ai-starter-vector-store-weaviate`)
- [x] Fixed `FilterExpressionBuilder` API (`Op` vs `Expression`) for Spring AI 1.0.0
- [x] Fixed `Document.getText()` API (was `getContent()`) for Spring AI 1.0.0
- [x] ArchUnit `allowEmptyShould(true)` on application-layer rules (no app layer yet)

### P02.1 — Application Layer Foundation ✅

- [x] `DomainEventPublisher` port (application.shared)
- [x] `SpringDomainEventPublisher` adapter (infrastructure.event)
- [x] Application exceptions: `ApplicationException`, `ResourceNotFoundException`, `DuplicateResourceException`
- [x] 12 domain events in `application.event` package
- [x] `DocumentApplicationService` with register, get, list, updateMetadata, delete
- [x] `ConversationApplicationService` with create, get, list, addUserMessage, rename, delete
- [x] `ChatSessionApplicationService` with start, recordTurn, complete, timeout, find, list
- [x] `QueryApplicationService` with submit, get, list
- [x] `UserApplicationService` with register, get, findByEmail, activate, deactivate, changePassword, assignRole, removeRole
- [x] Commands: 13 command records across all packages
- [x] Hexagonal boundaries verified (ArchUnit 8/8 pass)

### P02.2 — Business Use Cases 🚧

- [x] `UploadDocumentUseCase` — filename + format-filename consistency validation
- [x] `GetDocumentUseCase` — retrieves document scoped to tenant
- [x] `ListDocumentsUseCase` — lists by tenant or by owner (`executeByOwner` overload)
- [x] `DeleteDocumentUseCase` — soft-deletes with required-param validation
- [x] `CreateConversationUseCase` — title validation (not blank, ≤ 500 chars)
- [x] `GetConversationUseCase` — retrieves conversation scoped to user
- [x] `ListConversationsUseCase` — lists by user and tenant
- [x] `DeleteConversationUseCase` — guards: rejects if active `ChatSession` exists (cross-service)
- [x] `StartChatSessionUseCase` — validates modelId not blank
- [x] `CompleteChatSessionUseCase` — transitions active session to COMPLETED
- [x] `TimeoutChatSessionUseCase` — transitions active session to TIMED_OUT
- [x] `CreateKnowledgeQueryUseCase` — queryText validation (not blank, ≤ 10 000 chars)
- [x] `GetKnowledgeQueryUseCase` — retrieves query by ID
- [x] `ListKnowledgeQueriesUseCase` — lists by user and tenant
- [x] `RegisterUserUseCase` — email format, passwordHash, roles validation
- [x] `GetUserUseCase` — retrieves user by ID
- [x] `DeactivateUserUseCase` — deactivates user within tenant
- [x] `docs/releases/v0.3.0.md` created (Work In Progress)
- [x] BUILD SUCCESSFUL, all tests pass

### P03 — Document Ingestion Pipeline ⏳

- [ ] Tika document parser adapter
- [ ] Format-specific chunking strategies (semantic, slide, row)
- [ ] Async ingestion pipeline with state machine transitions
- [ ] Batch embedding via nomic-embed-text

### P04 — Retrieval Pipeline ⏳

- [ ] PostgreSQL BM25 full-text search adapter
- [ ] Hybrid search fusion (alpha-weighted)
- [ ] Metadata pre-filtering bridge to Weaviate
- [ ] Re-ranking port + Noop adapter
- [ ] Context assembly with token-budget guard

### P05 — REST API + Security ⏳

- [ ] JWT RS256 filter chain (`SecurityFilterChain`)
- [ ] `JwtService` (issue, validate, rotate)
- [ ] REST controllers (document, conversation, query)
- [ ] Request/response DTOs
- [ ] `@PreAuthorize` on application services
- [ ] OpenAPI spec

### P06 — Conversational AI ⏳

- [ ] Sliding-window conversation memory
- [ ] Query rewriting via Ollama
- [ ] Prompt builder with system-prompt isolation
- [ ] Streaming SSE response
- [ ] Citation extraction from `[SOURCE-N]` markers

### P07 — MCP Integration ⏳

- [ ] MCP server scaffold
- [ ] Knowledge base query tool
- [ ] Document ingestion tool
- [ ] Session management tool

### P08 — LangGraph + Agentic AI ⏳

- [ ] LangGraph graph definition
- [ ] Retrieval grader node
- [ ] Self-correction loop
- [ ] Multi-agent coordination

---

## Release Roadmap

| Version | Phase | Scope | Status |
|---|---|---|---|
| v0.1.0 | P01 | Infrastructure completion — persistence adapters, config properties | ✅ Complete |
| v0.2.0 | P02 | Application layer — use case orchestrators | ⏳ Planned |
| v0.3.0 | P03 | Document ingestion — Tika, chunking, async pipeline | ⏳ Planned |
| v0.4.0 | P04 | Retrieval pipeline — hybrid search, re-rank, context assembly | ⏳ Planned |
| v0.5.0 | P05 | REST API + Security — JWT, RBAC, controllers, OpenAPI | ⏳ Planned |
| v0.6.0 | P06 | Conversational AI — memory, query rewriting, streaming, citations | ⏳ Planned |
| v0.7.0 | P07 | MCP integration — knowledge base and ingestion tools | ⏳ Planned |
| v0.8.0 | P08 | LangGraph + agentic AI — self-correction, multi-agent | ⏳ Planned |
| v0.9.0 | — | Production hardening — observability, load testing, docs | ⏳ Planned |

---

## Architecture Freeze

The hexagonal architecture is **frozen**. The following may not be changed:

- Package structure (`domain`, `infrastructure`, `api`, `application`)
- Domain aggregate boundaries
- Port interface contracts
- Persistence entity hierarchy (`BaseUuidEntity → AuditableEntity`)
- Mapper and adapter naming conventions
- ArchUnit layering rules

---

## Current Progress

| Layer | Status | Notes |
|:---|:---:|---|
| Domain model | ✅ | All aggregates, value objects, port interfaces complete |
| PostgreSQL schema | ✅ | V001–V014 Flyway migrations, all tables, indexes, constraints |
| Persistence adapters | ✅ | All 7 adapters: Document, Chunk, Conversation, ChatSession, DocumentTag, AuditLog, User |
| Weaviate adapter | ✅ | VectorStore port impl, MetadataFilterTranslator, batch indexing |
| Config properties | ✅ | `AppProperties` covering ingestion, retrieval, conversation, storage |
| Application layer | 🚧 | Foundation complete (P02.1) — business use cases pending (P02.2) |
| REST API | ⏳ | Not started (P05) |
| Security | ⏳ | Not started (P05) |
| Ingestion pipeline | ⏳ | Not started (P03) |
| Retrieval pipeline | ⏳ | Not started (P04) |

---

## Table of Contents

- [Technology Stack](#technology-stack)
- [Supported Document Formats](#supported-document-formats)
- [Core Features](#core-features)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Security Model](#security-model)
- [Documentation](#documentation)
- [Roadmap](#roadmap)

---

## Technology Stack

| Category | Technology | Version | Role |
|---|---|---|---|
| **Runtime** | Java | 21 (LTS) | Virtual threads, records, pattern matching |
| **Framework** | Spring Boot | 3.5+ | Application container, auto-configuration |
| **AI Orchestration** | Spring AI | 1.0.0 | Unified LLM, embedding, vector store abstraction |
| **LLM** | Qwen3 via Ollama | Latest | Local generation — no data leaves the network |
| **Embedding** | nomic-embed-text via Ollama | Latest | 768-dim dense embeddings |
| **Vector Store** | Weaviate | 1.25+ | ANN search, BM25, native multi-tenancy |
| **Relational DB** | PostgreSQL | 16+ | Metadata, auth, conversation memory, FTS, audit |
| **ORM** | Hibernate 6 / Spring Data JPA | Bundled | JPA with JSONB and array type support |
| **Migrations** | Flyway | 10+ | Versioned, validated schema migrations |
| **Document Parsing** | Apache Tika | 2.9+ | Multi-format extraction with magic-byte validation |
| **Security** | Spring Security + JJWT | 0.12+ | JWT RS256, RBAC, filter chain |
| **Containerisation** | Docker + Compose | Latest | Reproducible local and production environments |
| **Observability** | Micrometer + Prometheus + Grafana | Latest | Metrics, tracing, dashboards |

---

## Supported Document Formats

| Format | Extension(s) | Chunking Strategy |
|---|---|---|
| PDF | `.pdf` | Semantic — sentence-boundary aware |
| Word | `.docx` | Semantic — paragraph aware |
| PowerPoint | `.pptx` | Slide-level chunking |
| Excel | `.xlsx` | Row-batch chunking |
| Plain Text | `.txt` | Sentence-window chunking |
| CSV | `.csv` | Row-batch chunking |
| HTML | `.html`, `.htm` | Tag-stripped sentence-window |
| Markdown | `.md`, `.markdown` | Header-aware sentence-window |

Format detection uses Apache Tika's magic-byte analysis — not filename extension — preventing disguised file uploads.

---

## Core Features

### Document Ingestion Pipeline

- Multi-format extraction via Apache Tika
- Format-specific chunking strategies (semantic, row-based, slide-level)
- Batch embedding via `nomic-embed-text` (768-dim, configurable batch size)
- Dual storage: chunk text in PostgreSQL (BM25), vectors in Weaviate (ANN)
- Async pipeline with dedicated thread pool — ingestion never blocks query serving
- Status state machine: `PENDING → PARSING → CHUNKING → EMBEDDING → INDEXED`
- Automatic rollback to `FAILED` with error capture on any pipeline stage

### Retrieval Pipeline

- **Query rewriting** — LLM rewrites the user query to be self-contained and search-optimised, accounting for conversational context
- **Hybrid search** — BM25 keyword search (PostgreSQL FTS) combined with ANN vector search (Weaviate), weighted alpha=0.75 vector-heavy by default
- **Metadata pre-filtering** — filter by department, classification, tags before vector search
- **Over-fetch and re-rank** — retrieve top-20 candidates; re-ranking service collapses to top-5 (Noop by default, pluggable cross-encoder)
- **Token-budget-aware context assembly** — never silently truncate; respect Qwen3's context window

### Generation Pipeline

- Conversational memory with configurable sliding window (default: 10 messages)
- System-prompt isolation — prevents prompt injection from user queries
- Streaming responses via Server-Sent Events
- Source citations extracted from `[SOURCE-N]` markers in the LLM response
- Every response is linked to the source chunks that grounded it

### Security

- JWT RS256 access tokens (15-minute TTL) + rotating refresh tokens (7-day TTL)
- Four roles: `ADMIN`, `USER`, `VIEWER`, `AUDITOR`
- Method-level `@PreAuthorize` on the service layer — not just the controller
- Tenant isolation enforced at application layer and Weaviate native tenancy
- Append-only audit log with immutability enforced by PostgreSQL `RULE`

---

## Architecture

Project EKA is built as a **Modular Monolith** with strict **Hexagonal Architecture** (Ports & Adapters). The domain layer is pure Java — zero framework dependencies — making it independently testable and provider-agnostic.

```
api/          →  application/  →  domain/  ←  infrastructure/
(HTTP, DTOs)     (use cases)      (pure)       (JPA, Weaviate, Ollama)
```

Every AI integration — embedding, generation, vector storage — is accessed through a port interface. Swapping Ollama for AWS Bedrock, or Weaviate for pgvector, requires changing only the infrastructure adapter, with zero domain or application changes.

For detailed architecture documentation see:

- [Architecture Overview](docs/architecture.md)
- [Logical Architecture](docs/logical-architecture.md)
- [Component Architecture](docs/component-architecture.md)
- [Roadmap](docs/roadmap.md)

---

## Quick Start

### Prerequisites

- Docker 24+
- Docker Compose v2
- 16 GB RAM recommended (Ollama + Weaviate + PostgreSQL)
- NVIDIA GPU optional but recommended for Qwen3

### 1. Clone and configure

```bash
git clone https://github.com/your-org/project-eka.git
cd project-eka
cp .env.example .env
# Edit .env — set JWT_SECRET_KEY, DB_PASSWORD, WEAVIATE_API_KEY
```

### 2. Start infrastructure

```bash
docker compose up -d postgres weaviate ollama
```

### 3. Pull models (first run only)

```bash
docker exec -it ollama ollama pull qwen3
docker exec -it ollama ollama pull nomic-embed-text
```

### 4. Run the application

```bash
./gradlew bootRun
```

### 5. Verify

```
GET http://localhost:8080/actuator/health
```

The Flyway migrations run automatically on startup. The default `ADMIN` role is seeded by `V010__seed_roles.sql`.

---

## Configuration

All configuration is driven by environment variables. The `application.yml` provides defaults for local development.

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/knowledge_assistant` |
| `DB_USERNAME` | Database username | `ka_user` |
| `DB_PASSWORD` | Database password | — |
| `OLLAMA_URL` | Ollama base URL | `http://localhost:11434` |
| `OLLAMA_CHAT_MODEL` | Generation model name | `qwen3` |
| `OLLAMA_EMBEDDING_MODEL` | Embedding model name | `nomic-embed-text` |
| `WEAVIATE_HOST` | Weaviate host:port | `localhost:8080` |
| `WEAVIATE_API_KEY` | Weaviate API key | — |
| `JWT_SECRET_KEY` | JWT signing key (RS256 private key) | — required |
| `JWT_ACCESS_EXPIRY_MS` | Access token TTL in milliseconds | `900000` (15 min) |
| `JWT_REFRESH_EXPIRY_MS` | Refresh token TTL in milliseconds | `604800000` (7 days) |
| `DOCUMENT_STORAGE_ROOT` | Raw file storage path | `/data/documents` |

---

## Security Model

### Roles and permissions

| Action | ADMIN | USER | VIEWER | AUDITOR |
|---|---|---|---|---|
| Upload document | ✓ | ✓ | — | — |
| Delete document | ✓ | Own only | — | — |
| Query / chat | ✓ | ✓ | ✓ | — |
| View documents | ✓ | ✓ | ✓ | — |
| View audit logs | ✓ | — | — | ✓ |
| Manage users | ✓ | — | — | — |
| System configuration | ✓ | — | — | — |

### JWT token claims

```json
{
  "sub":   "user-uuid",
  "tid":   "tenant-uuid",
  "roles": ["USER"],
  "iat":   1234567890,
  "exp":   1234568790,
  "jti":   "unique-token-id"
}
```

Refresh tokens are stored as SHA-256 hashes — the raw token never touches the database.

---

## Documentation

| Document | Description |
|---|---|
| [Architecture Overview](docs/architecture.md) | High-level system design, technology decisions, and principles |
| [Logical Architecture](docs/logical-architecture.md) | Layer responsibilities, dependency rules, and data flow |
| [Component Architecture](docs/component-architecture.md) | Service responsibilities, RAG pipelines, and sequence flows |
| [Roadmap](docs/roadmap.md) | Delivery phases, MCP integration, LangGraph, multi-agent plans |
| [Executive Summary](docs/executive-summary.md) | Non-technical summary for architects and engineering managers |

---

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| P01 | Infrastructure Completion — persistence adapters, config properties, Spring AI 1.0 fixes | ✅ Complete |
| P02.1 | Application Layer Foundation — services, commands, events, DomainEventPublisher | 🚧 In Progress |
| P02.2 | Business Use Cases — ingestion, retrieval, and chat orchestrators (deferred to P03–P06) | ⏳ Planned |
| P03 | Document Ingestion — Tika, format chunking, async pipeline, batch embedding | ⏳ Planned |
| P04 | Retrieval Pipeline — hybrid search, BM25, re-rank, context assembly | ⏳ Planned |
| P05 | REST API + Security — JWT RS256, RBAC, controllers, DTOs, OpenAPI | ⏳ Planned |
| P06 | Conversational AI — memory window, query rewriting, streaming, citations | ⏳ Planned |
| P07 | MCP Server Integration — knowledge base tools, ingestion tools | ⏳ Future |
| P08 | LangGraph + Agentic AI — self-correction, multi-agent coordination | ⏳ Future |

See [docs/roadmap.md](docs/roadmap.md) for full detail on each phase.

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Ensure ArchUnit tests pass: `./gradlew test --tests HexagonalArchitectureTest`
4. Submit a pull request with a clear description of changes

## License

[MIT License](LICENSE) — see LICENSE file for details.
