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
- Mandatory tenant isolation: every retrieval query is scoped to the caller's `TenantId`; cross-tenant access is architecturally impossible
- Score normalization: Weaviate certainty scores clamped to `[0.0, 1.0]` before entering the domain
- `MetadataFilter` support: caller-supplied filter criteria merged with tenant isolation filter
- `RetrievalOptions` enforcement: `topK` and `minimumScore` respected on every retrieval
- Infrastructure exception translation: Weaviate and database errors wrapped as `RetrievalAdapterException`
- 20 new automated tests across mapper and adapter; 145 total tests, 0 failures

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
