<div align="center">

<img src="docs/images/banner.svg" alt="Project EKA — Enterprise Knowledge Assistant" width="900"/>

# Project EKA — Enterprise Knowledge Assistant

*A reference implementation of an enterprise-grade Retrieval-Augmented Generation (RAG) platform — built with Hexagonal Architecture, Spring AI, and fully on-premises AI models — with complete data ownership and no external API dependencies.*

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.0-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring_AI-1.0.0-6DB33F?style=flat-square&logo=spring&logoColor=white)](https://spring.io/projects/spring-ai)
[![Weaviate](https://img.shields.io/badge/Weaviate-1.25-FF6D00?style=flat-square)](https://weaviate.io)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Apache Tika](https://img.shields.io/badge/Apache_Tika-2.9.2-D22128?style=flat-square&logo=apache&logoColor=white)](https://tika.apache.org/)
[![Tests](https://img.shields.io/badge/tests-673_passing-22c55e?style=flat-square)](docs/releases/v0.4.0.md)
[![License](https://img.shields.io/badge/license-MIT-64748b?style=flat-square)](LICENSE)

</div>

---

## Highlights

- Hexagonal Architecture (Ports & Adapters) — ArchUnit-enforced at build time
- Domain-Driven Design — pure domain model with zero framework dependencies
- Spring AI 1.0.0 — unified abstraction over embedding models and vector stores
- Apache Tika 2.9.2 — multi-format document parsing with magic-byte detection
- Ollama — local embedding (`nomic-embed-text`, 768-dim) and generation (`qwen3`)
- Weaviate 1.25 — vector store with native multi-tenancy support
- PostgreSQL 16 — relational metadata, full-text search, append-only audit log
- Hybrid Search *(v0.5.0)*
- MCP Ready *(v0.8.0)*
- LangGraph Ready *(v0.9.0)*
- 673 Automated Tests, 0 failures

---

## What is Project EKA?

Project EKA (Enterprise Knowledge Assistant) is an open-architecture exploration of how modern AI can transform scattered enterprise documents into a secure, searchable, and conversational knowledge platform — with full data ownership and on-premises deployment.

The platform is built to evolve from basic document retrieval to autonomous multi-agent workflows. Every AI touchpoint (embedding, generation, vector storage, document parsing) sits behind a port interface. Swapping Ollama for AWS Bedrock, or Weaviate for pgvector, requires changing one adapter with zero domain or application changes.

---

## Why Project EKA?

Most RAG implementations are demos. They work for a single user, on a single machine, with hardcoded API keys, and collapse the moment a real requirement is added. Project EKA is built differently.

| Differentiator | What it means in practice |
|---|---|
| **Hexagonal Architecture — enforced** | The build fails if an infrastructure class is imported into the domain layer. Eight ArchUnit rules run on every `gradle test`. Architecture is not a convention — it is a constraint. |
| **Zero external API dependency** | No OpenAI key. No cloud vector store account. Ollama, Weaviate, and PostgreSQL run locally via Docker. Complete data ownership from day one. |
| **Multi-tenancy as a first-class citizen** | Every entity carries `TenantId`. Weaviate uses native per-tenant collections — not property-based filtering. This is in V001 of the schema, not retrofitted. |
| **Provider independence is real** | To swap the embedding provider, implement `EmbeddingProvider` (one file) and update `application.yml`. Zero domain or application layer changes — enforced by the port boundary, not by documentation. |
| **Document lifecycle as a state machine** | `PENDING → PARSING → CHUNKING → EMBEDDING → INDEXED` with valid transitions enforced in the domain aggregate. Not a status field — a state machine. |
| **Built for LangGraph and MCP** | Every application service is stateless. The `KnowledgeQuery` aggregate holds retrieval state. Port interfaces align with what MCP tools and LangGraph nodes expect. Adopting these frameworks requires zero domain rewrites. |

---

## Current Status

| | |
|---|---|
| **Current Release** | v0.7.0 — P06.1: Product Completeness & Authorization Depth (Phase 6, milestone 1) |
| **Document Pipeline** | `PENDING → PARSING → CHUNKING → EMBEDDING → INDEXED` ✅ |
| **Automated Tests** | 673 passing, 0 failures · 67 test classes |
| **ArchUnit Rules** | 8 enforced at build time |
| **CI** | GitHub Actions — build + full test suite + ArchUnit on every PR and push to `main` |
| **Schema Migrations** | Flyway V001–V017 (17 migrations) |
| **Current Focus** | P06.1 — Product Completeness & Authorization Depth — REST Surface Foundation (complete) |
| **Next Milestone** | P06.2 — Authorization Filter (Phase 6, milestone 2 of 5; v0.7.1) — not yet started |

---

## Current Capabilities

**Implemented — v0.4.0 (ingestion foundation)**

- ✅ Multi-format document upload with format-filename consistency validation — implemented at the application layer (`UploadDocumentUseCase`) and fully tested; **REST-exposed as of P06.1** via `POST /api/v1/documents` (multipart, closes v0.6.1 ADR EX09's deferral and post-Phase-5 audit finding H2). See "Application Platform" below.
- ✅ Apache Tika parsing with magic-byte format detection
- ✅ Token-aware sliding window chunking with paragraph-boundary snapping
- ✅ Batch embedding generation via Ollama (`nomic-embed-text`, 768-dim)
- ✅ Weaviate vector indexing with idempotent re-index support
- ✅ Delete synchronization — Weaviate vectors and chunks removed on document delete
- ✅ Ingestion validation — vector count, duplicate detection, provenance checks
- ✅ Ingestion benchmark — per-phase timing (chunk / embed / index / persist) and throughput

**Implemented — v0.5.0 (retrieval & generation)**

- ✅ BM25 keyword search (PostgreSQL full-text search) + ANN semantic search (Weaviate)
- ✅ Hybrid search fusion (alpha-weighted Reciprocal Rank Fusion)
- ✅ Query rewriting via Ollama
- ✅ Context assembly with token-budget guard
- ✅ Chat generation with output guardrails, citation engine, and persisted conversational memory

**Implemented — v0.5.1–v0.6.0 (application platform, Phase 5 complete)**

- ✅ REST API with OpenAPI/Swagger specification (`/api/v1/conversations`)
- ✅ End-to-end RAG orchestration — one HTTP request drives retrieval → context assembly → generation → citation → persistence
- ✅ JWT authentication (HS256) — `POST /api/v1/auth/login` issues an access token; every other endpoint requires one
- ✅ Role authorization — creating a conversation or sending a message requires `USER` or `ADMIN`; `VIEWER`/`AUDITOR` are read-only
- ✅ Tenant isolation and resource ownership — a conversation is reachable only by the user who created it, within its own tenant; every other case (including a different user in the *same* tenant) returns `404`, not `403`; tenant checks now cover every ownership-scoped `ConversationApplicationService` method, including the not-yet-routed `rename`/`delete` (v0.6.0)
- ✅ Observability — `/actuator/health`+`/actuator/info` (public), `/actuator/metrics`+`/actuator/prometheus` (JWT-gated by default, or isolated onto a separate `MANAGEMENT_PORT` — v0.6.0); custom Ollama/Weaviate health checks; retrieval/generation/orchestration latency via Micrometer `Observation`; auth/authz failure counters; correlation IDs on every request; structured (ECS JSON) console logging
- ✅ Operational hardening (v0.6.0) — short, per-step transactions through the upload pipeline instead of one long transaction; failed ingestion now marks the document `FAILED` with an error message instead of leaving it stuck mid-pipeline; bounded Ollama HTTP connect/read timeouts; infrastructure failures from retrieval consistently surface as `502`, oversized/blank queries as `400`; no default database password outside `dev`/`test`

**Implemented — v0.6.1 (engineering excellence & repository governance, post-Phase-5)**

- ✅ CI/CD — GitHub Actions builds, tests, and runs ArchUnit on every pull request and push to `main`; branch protection specified in [docs/governance/branch-protection.md](docs/governance/branch-protection.md)
- ✅ Correct HTTP status codes for client mistakes — malformed JSON, a non-UUID path variable, and similar framework-level errors now return `400`, not a misleading `500`
- ✅ JWT configuration fails fast at startup — a too-short HS256 secret or non-positive token expiry stops the application at boot, not on the first login request
- ✅ Login rate limiting — `POST /api/v1/auth/login` is capped per source IP (10 attempts/minute by default)
- ✅ Request size limits — oversized request bodies are rejected before they reach application code
- ✅ Application `Dockerfile` (multi-stage, non-root) — closes the gap where only the *dependencies* (Postgres/Weaviate/Ollama) were containerized
- ✅ Single source of truth for the release version (`build.gradle` → `/actuator/info`'s `build.version`, no more hand-maintained duplicate)

**Implemented — v0.7.0 / P06.1 (Product Completeness & Authorization Depth — REST Surface Foundation, Phase 6 milestone 1)**

- ✅ Document ingestion REST API — `POST /api/v1/documents` (multipart upload), `GET /{id}`, `GET` (paginated list), `DELETE /{id}`; documents are tenant-wide readable (shared knowledge base), not owner-scoped like conversations
- ✅ First-user bootstrap and minimal admin surface — public `POST /api/v1/admin/bootstrap` (once per tenant), `ADMIN`-only `POST /api/v1/admin/users`, `GET /api/v1/admin/users/{id}`, `POST /api/v1/admin/users/{id}/deactivate`
- ✅ Conversation list and delete — `GET /api/v1/conversations` (own conversations, paginated), `DELETE /api/v1/conversations/{id}`
- ✅ Closed a real cross-tenant read gap in user administration — `getUser`/`activateUser`/`deactivateUser` now verify tenant ownership, not just a valid ID
- ✅ Application-layer validation errors (e.g. `DeleteConversationUseCase`'s active-session guard) now return `400`, not a misleading `500`
- ✅ Request-size protection narrowed to support real uploads without weakening the general limit — only the upload route is exempt, matched on method + path + content type together

**Planned — Phase 6 (continued) and beyond**

- ⏳ Authorization Filter (P06.2) — fine-grained, metadata-based retrieval-pipeline authorization; named in this project's target architecture since before Phase 4, still unbuilt
- ⏳ Server-Sent Events streaming responses with source citations
- ⏳ MCP server — knowledge base and ingestion exposed as MCP tools
- ⏳ LangGraph agentic pipeline with self-correction loop
- ⏳ Configurable Weaviate client timeout — no configuration surface exists in Spring AI 1.0.0 today (see `.claude/DECISIONS.md`, ADR HD03)

---

## Architecture

Project EKA is built as a **Modular Monolith** with strict **Hexagonal Architecture** (Ports & Adapters):

```
api/          →  application/  →  domain/  ←  infrastructure/
(HTTP, DTOs)     (use cases)      (pure)       (JPA, Weaviate, Ollama, Tika)
```

- **Domain** — pure Java aggregates, value objects, and port interfaces; zero framework dependencies
- **Application** — use cases, commands, domain events; no infrastructure imports
- **Infrastructure** — JPA adapters, Weaviate adapter, Ollama adapter, Tika adapter, file storage
- **API** — REST controllers, JWT authentication, tenant/role authorization, observability (`v0.5.1`–`v0.5.4`)
- **ArchUnit** — 8 layering rules enforced at build time; violations fail the build

### High-Level Architecture

![High-Level Architecture](docs/diagrams/architecture.svg)

### Package Structure

![Package Structure](docs/diagrams/package-structure.svg)

### Document Ingestion Pipeline

![Document Ingestion Pipeline](docs/diagrams/ingestion-pipeline.svg)

### Retrieval & RAG Pipeline

![Retrieval Pipeline](docs/diagrams/retrieval-pipeline.svg)

For detailed architecture documentation see [docs/architecture/overview.md](docs/architecture/overview.md), [docs/architecture/logical.md](docs/architecture/logical.md), and [docs/architecture/components.md](docs/architecture/components.md).

---

## Technology Stack

### Implemented

| Category | Technology | Version | Role |
|---|---|---|---|
| Runtime | Java | 21 (LTS) | Virtual threads, records, pattern matching (`--enable-preview`) |
| Framework | Spring Boot | 3.5.0 | Application container and auto-configuration |
| AI Orchestration | Spring AI | 1.0.0 | Unified embedding and vector store abstraction |
| Embedding | nomic-embed-text via Ollama | Latest | Local 768-dim dense embeddings |
| Vector Store | Weaviate | 1.25 | ANN search, native multi-tenancy |
| Relational DB | PostgreSQL | 16 | Metadata, audit log, BM25 full-text search |
| ORM | Hibernate 6 / Spring Data JPA | Bundled | JPA persistence with custom domain mappers |
| Migrations | Flyway | 10+ | Versioned schema migrations (V001–V017) |
| Document Parsing | Apache Tika | 2.9.2 | Multi-format extraction, magic-byte detection |
| Generation | Qwen3 via Ollama | Latest | Local LLM for query rewriting and chat generation |
| Security | Spring Security + JJWT 0.12+ | — | JWT (HS256) authentication (v0.5.2); role + tenant/ownership authorization (v0.5.3, extended to every ownership-scoped method in v0.6.0) |
| Observability | Spring Boot Actuator + Micrometer + Micrometer Observation | — | Health, metrics, request/latency instrumentation, correlation IDs, structured logging (v0.5.4); optional separate management port (v0.6.0); no Prometheus/Grafana deployment yet — see Planned |
| Architecture Testing | ArchUnit | 1.3.0 | Hexagonal layering enforcement |
| Build | Gradle | 8.12 | |

### Planned

| Category | Technology | Phase | Role |
|---|---|---|---|
| Observability Deployment | Prometheus + Grafana | Future | Scrape `/actuator/prometheus`, dashboards (metrics themselves already exist as of v0.5.4; separate management port available as of v0.6.0) |
| Weaviate client timeout | Custom `WeaviateClient` bean override | Future | No configuration surface exists in Spring AI 1.0.0 today — deferred, see `.claude/DECISIONS.md` ADR HD03 |
| AI Protocol | Spring MCP Server | v0.8.0 | Expose knowledge base as MCP tools |
| Graph Orchestration | LangGraph4j | v0.9.0 | Agentic retrieval with self-correction |

---

## Quick Start

### Prerequisites

- Java 21+
- Docker and Docker Compose v2
- 8 GB RAM minimum (Weaviate + PostgreSQL + Ollama)
- Gradle Wrapper (included)

### 1. Start infrastructure

```bash
docker compose up -d postgres weaviate ollama
```

### 2. Pull the embedding model (first run only)

```bash
docker exec -it ollama ollama pull nomic-embed-text
```

### 3. Run the application

```bash
./gradlew bootRun
```

Flyway migrations run automatically on startup.

**Alternative — Docker (v0.6.1):** build and run the application itself as a container (the
three infrastructure dependencies above still need to be reachable at the URLs in
[Configuration](#configuration)):

```bash
docker build -t project-eka .
docker run -p 8080:8080 \
  -e DB_PASSWORD=... -e JWT_SECRET_KEY=... \
  project-eka
```

### 4. Verify

```
GET http://localhost:8080/actuator/health
GET http://localhost:8080/actuator/info
```

Both are public (v0.5.4). `/actuator/metrics` and `/actuator/prometheus` require a JWT like every
other non-public endpoint — see [Observability](#observability) below. Every response, success or
error, carries an `X-Correlation-Id` header.

### 5. Authenticate

Every endpoint under `/api/v1/conversations` requires a JWT (v0.5.2). A **tenant** row still has
to be seeded directly (e.g. via a migration or a one-off script) — there is no tenant-creation
endpoint (P06.1, ADR PC03; tenant provisioning is deliberately an ops/database concern, not
application scope). Once a tenant exists, its first `ADMIN` user no longer needs to be seeded
directly — `POST /api/v1/admin/bootstrap` creates it (works exactly once per tenant):

```
POST /api/v1/admin/bootstrap
{"tenantId": "<uuid>", "email": "admin@example.com", "password": "at-least-8-chars"}

→ 201 {"id": "...", "email": "admin@example.com", "roles": ["ADMIN"], "active": true, ...}
```

Then log in as usual:

```
POST /api/v1/auth/login
{"tenantId": "<uuid>", "email": "admin@example.com", "password": "at-least-8-chars"}

→ {"accessToken": "...", "tokenType": "Bearer", "expiresInMs": 900000}
```

Send the returned token as `Authorization: Bearer <accessToken>` on every subsequent request.
As of v0.5.3, role also matters: creating a conversation, sending a message, uploading or
deleting a document, or deleting a conversation all require `USER` or `ADMIN` — a
`VIEWER`/`AUDITOR` token gets `403` on those. Reading/listing conversations and documents is
open to all four roles, subject to ownership (conversations) or tenant membership (documents).
Registering additional users (`POST /api/v1/admin/users`) requires an authenticated `ADMIN` token.

### Configuration

Override via environment variables or `application.yml`:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/project_eka` |
| `DB_USERNAME` | `ka_user` |
| `DB_PASSWORD` | — required |
| `JWT_SECRET_KEY` | — required (no default outside `dev`/`test` profiles); must be ≥32 bytes (HS256) or the application refuses to start (v0.6.1, ADR EX04) |
| `JWT_ACCESS_EXPIRY_MS` | `900000` (15 min) |
| `LOGIN_RATE_LIMIT_PER_MINUTE` | `10` — max `POST /api/v1/auth/login` attempts per source IP per minute (v0.6.1, ADR EX05) |
| `MAX_REQUEST_BODY_BYTES` | `1048576` (1 MiB) — requests with a larger `Content-Length` are rejected with `413` before parsing (v0.6.1, ADR EX06) |
| `OLLAMA_URL` | `http://localhost:11434` |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` |
| `OLLAMA_CHAT_MODEL` | `qwen3` |
| `WEAVIATE_SCHEME` | `http` |
| `WEAVIATE_HOST` | `localhost:8080` |
| `WEAVIATE_API_KEY` | — optional |
| `DOCUMENT_STORAGE_ROOT` | `/data/documents` |
| `SERVER_PORT` | `8080` |
| `MANAGEMENT_PORT` | same as `SERVER_PORT` — set to a different value to isolate actuator endpoints onto a separate port, outside the JWT-based `SecurityFilterChain` (v0.6.0) |
| `app.ollama.connect-timeout-ms` / `app.ollama.read-timeout-ms` | `5000` / `60000` — Ollama HTTP connect/read timeouts (v0.6.0; standard Spring relaxed binding, e.g. `APP_OLLAMA_CONNECT_TIMEOUT_MS`) |

### Observability

- `GET /actuator/health` (public) — aggregates DB connectivity plus custom `ollama`/`weaviate`
  checks; `/actuator/health/liveness` and `/actuator/health/readiness` are also available
- `GET /actuator/info` (public) — static app name/version
- `GET /actuator/metrics`, `GET /actuator/prometheus` (require a JWT, like every other non-public
  endpoint, unless `MANAGEMENT_PORT` is set to isolate actuator onto its own port — v0.6.0) —
  retrieval/generation/orchestration latency, `http.server.requests`, and
  `eka.auth.failures` / `eka.authz.failures` counters
- Every response carries an `X-Correlation-Id` header (generated, or echoed back if you send one)
- Console logs are structured JSON (Elastic Common Schema) in every profile, with the correlation
  ID automatically included on every line for a request

---

## Documentation

| Document | Description |
|---|---|
| [Architecture Overview](docs/architecture/overview.md) | High-level system design, technology decisions, and guiding principles |
| [Logical Architecture](docs/architecture/logical.md) | Layer responsibilities, dependency rules, and data flow |
| [Component Architecture](docs/architecture/components.md) | Service responsibilities, RAG pipeline design, and sequence flows |
| [Executive Summary](docs/architecture/executive-summary.md) | Non-technical overview for architects and engineering managers |
| [Roadmap](docs/roadmap.md) | Detailed phase design, MCP integration, LangGraph, and multi-agent plans |
| [Release Notes v0.4.0](docs/releases/v0.4.0.md) | Document ingestion pipeline — full changelog |
| [Release Notes v0.3.0](docs/releases/v0.3.0.md) | Application layer — full changelog |

---

## Release Roadmap

![Release Roadmap](docs/diagrams/roadmap.svg)

See [docs/roadmap.md](docs/roadmap.md) for full phase design and success criteria. This table
predates Phase 5's finer-grained milestone tracking (P05.1–P05.5); see
[.claude/ROADMAP.md](.claude/ROADMAP.md) and [.claude/PROJECT_STATE.md](.claude/PROJECT_STATE.md)
for the authoritative, currently-maintained status of everything from v0.5.1 onward.

| Version | Scope | Status |
|---|---|---|
| v0.1.0 | Architecture Foundation — hexagonal scaffold, ArchUnit, domain model | ✅ Complete |
| v0.2.0 | Persistence Foundation — Flyway schema, JPA entities, repository adapters | ✅ Complete |
| v0.3.0 | Application Layer — use cases, domain events, commands, 46 tests | ✅ Complete |
| v0.4.0 | Document Ingestion — Tika parsing, chunking, embedding, Weaviate indexing | ✅ Complete |
| v0.5.0 | Retrieval & RAG — hybrid search, query rewriting, context assembly, chat generation | ✅ Complete |
| v0.5.1 | REST API — end-to-end RAG orchestration, OpenAPI (P05.1) | ✅ Complete |
| v0.5.2 | JWT Authentication Foundation (P05.2) | ✅ Complete |
| v0.5.3 | Tenant & Role Authorization Boundary (P05.3) | ✅ Complete |
| v0.5.4 | Observability Foundation (P05.4) | ✅ Complete |
| v0.6.0 | Operational Hardening & Phase 5 Completion (P05.5) | ✅ Complete |
| v0.6.1 | Engineering Excellence & Repository Governance (post-Phase-5 audit response) | ✅ Complete |
| v0.7.0 | Phase 6, P06.1 — Product Completeness & Authorization Depth: REST surface foundation (document ingestion, admin/bootstrap, conversation list/delete) | ✅ Complete |
| v0.7.1 | Phase 6, P06.2 — Authorization Filter (retrieval-pipeline stage) | ⏳ Planned |
| v0.7.2 | Phase 6, P06.3 — not yet scoped | ⏳ Planned |
| v0.7.3 | Phase 6, P06.4 — not yet scoped | ⏳ Planned |
| v0.7.4 | Phase 6, P06.5 — Phase 6 Complete gate | ⏳ Planned |
| v0.8.0 | Phase 7 — Retrieval Quality & Operational Integrity: re-ranking, HyDE, Postgres↔Weaviate reconciliation, refresh tokens | ⏳ Planned |
| v0.9.0 | Phase 8 — Scale & Ecosystem Readiness: metrics dashboards, streaming, MCP spike (go/no-go) | ⏳ Planned |
| v1.0.0 | First Stable Release — gated on the full product definition in `.claude/PROJECT_STATE.md`'s "Roadmap to v1.0.0 (Frozen)" section | ⏳ Planned |

Table reassigned by the v1.0 Roadmap Freeze (ADR GOV03) — v0.7.0–v0.9.0 previously named streaming/MCP/LangGraph directly; that content still exists, just resequenced into Phase 7/8 above rather than driving version numbers on its own. Phase 6 versioning further refined by ADR GOV04 — one point release per P06.x milestone (v0.7.0–v0.7.4) rather than one version for the whole phase; Phase 7/8/v1.0.0 entry versions are unchanged. See `.claude/ROADMAP.md` and `.claude/PROJECT_STATE.md` for the authoritative, currently-maintained sequence.

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Ensure all tests and ArchUnit rules pass: `gradle test`
4. Submit a pull request with a clear description of changes

**Architecture constraints:** The hexagonal layer structure (`domain`, `application`, `infrastructure`, `api`), domain aggregate boundaries, port interface contracts, and all 8 ArchUnit rules are frozen. Review [docs/architecture/overview.md](docs/architecture/overview.md) before making structural changes.

For questions or feedback, open an issue.

---

## License

[MIT License](LICENSE)
