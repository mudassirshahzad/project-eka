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

## Current Progress

| Status | Milestone |
|:---:|---|
| ✅ | Architecture Foundation |
| ✅ | Domain Model |
| ✅ | PostgreSQL Schema |
| 🔄 | Weaviate Integration |
| ⏳ | Security Layer |
| ⏳ | Document Ingestion Pipeline |
| ⏳ | Retrieval Pipeline |
| ⏳ | Conversational AI |
| ⏳ | MCP Integration |
| ⏳ | LangGraph Integration |
| ⏳ | Agentic AI Platform |

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
| Phase 1 | Architecture Foundation, Domain Model, PostgreSQL Schema | ✅ Complete |
| Phase 2 | Weaviate Integration, Document Ingestion, Tika, Chunking | 🔄 In Progress |
| Phase 3 | Hybrid Retrieval, Generation, Streaming, Citations | ⏳ Planned |
| Phase 4 | Conversational Memory, Query Rewriting | ⏳ Planned |
| Phase 5 | Security Layer, Audit Logging, Observability | ⏳ Planned |
| Phase 6 | Re-ranking, Advanced Retrieval | Future |
| Phase 7 | MCP Server Integration | Future |
| Phase 8 | LangGraph Integration | Future |
| Phase 9 | Agentic AI Platform | Future |

See [docs/roadmap.md](docs/roadmap.md) for full detail on each phase including MCP, LangGraph, and Agentic AI integration plans.

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Ensure ArchUnit tests pass: `./gradlew test --tests HexagonalArchitectureTest`
4. Submit a pull request with a clear description of changes

## License

[MIT License](LICENSE) — see LICENSE file for details.
