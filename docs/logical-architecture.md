# Logical Architecture

> Layer responsibilities, dependency rules, data flow, and the Ports & Adapters contract for Project EKA (Enterprise Knowledge Assistant).

---

## Table of Contents

- [Layer Model](#layer-model)
- [Dependency Rules](#dependency-rules)
- [Layer Responsibilities](#layer-responsibilities)
- [Domain Model](#domain-model)
- [Port Contracts](#port-contracts)
- [Data Flow](#data-flow)
- [Multi-Tenancy Model](#multi-tenancy-model)
- [Event Model](#event-model)
- [Pagination Contract](#pagination-contract)

---

## Layer Model

The platform is structured as four concentric layers. Dependency flows inward only — outer layers know about inner layers; inner layers know nothing about outer layers.

```
┌─────────────────────────────────────────────────────────────────┐
│                          API Layer                               │
│   REST controllers · DTOs · Exception handlers · OpenAPI        │
│   Technology: Spring MVC, Jackson, Bean Validation              │
├─────────────────────────────────────────────────────────────────┤
│                      Application Layer                           │
│   Use case orchestrators · Cross-cutting services               │
│   Technology: Spring @Service, Spring @Async, @Transactional    │
├─────────────────────────────────────────────────────────────────┤
│                       Domain Layer                               │
│   Aggregates · Value objects · Domain services · Port interfaces │
│   Technology: Pure Java 21 — zero framework imports             │
├─────────────────────────────────────────────────────────────────┤
│                    Infrastructure Layer                          │
│   JPA adapters · Weaviate adapter · Ollama adapter · Tika       │
│   Technology: Spring Data JPA, Spring AI, Weaviate client       │
└─────────────────────────────────────────────────────────────────┘
```

---

## Dependency Rules

These rules are absolute. They are enforced at build time by ArchUnit. A violation is a CI failure.

### Allowed dependencies

| Layer | May depend on |
|---|---|
| API | Application, Domain |
| Application | Domain |
| Infrastructure | Domain |

### Forbidden dependencies (enforced)

| Layer | Must NOT depend on | Reason |
|---|---|---|
| Domain | Application | Domain must be use-case-neutral |
| Domain | Infrastructure | Domain must be technology-neutral |
| Domain | API | Domain must be transport-neutral |
| Domain | `jakarta.persistence.*` | No ORM leak into domain |
| Domain | `org.springframework.*` | No framework lock-in |
| Application | Infrastructure | Use cases must be testable without real infrastructure |
| Application | API | No circular dependency |
| Infrastructure | API | Adapter layer must not know about transport |

### Why this matters for AI platforms

AI provider APIs change frequently. A team that mixes Spring AI calls directly into service classes — a common pattern in tutorial code — couples every use case to a specific provider. When the provider changes API, raises prices, or fails compliance requirements, the refactor touches the entire codebase.

On this platform, `LLMPort` and `EmbeddingPort` are the only contracts the application layer sees. The Spring AI implementation detail is invisible above the infrastructure boundary.

---

## Layer Responsibilities

### API Layer (`api/`)

**Owns:** HTTP contract — request deserialisation, response serialisation, error mapping, OpenAPI documentation.

**Does not own:** Business logic, database access, AI calls.

**Key types:**
- `DocumentController`, `QueryController`, `ConversationController`, `AuthController`, `AdminController`
- Request/Response DTOs (named `*Request`, `*Response`)
- Mapper classes for DTO ↔ Domain translation
- `GlobalExceptionHandler` — maps domain exceptions to HTTP status codes

**Boundary rule:** The API layer receives a DTO, translates it to a domain object, calls an application use case, receives a result, and translates it back to a DTO. No business logic lives here.

### Application Layer (`application/`)

**Owns:** Use case orchestration — the "what to do" in response to a request, sequencing domain and infrastructure calls, managing transaction boundaries.

**Does not own:** Domain rules (those live in aggregates), infrastructure implementation details.

**Key services:**

| Service | Responsibility |
|---|---|
| `DocumentIngestionService` | Orchestrates the full parse → chunk → embed → store → persist pipeline |
| `QueryOrchestrationService` | Orchestrates rewrite → retrieve → rank → assemble → generate |
| `GenerationOrchestrationService` | Assembles prompt with memory, calls LLM, extracts citations |
| `ConversationService` | Creates conversations, appends messages, manages memory window |
| `AuditService` | Writes audit log entries — called from all other services |

**Transaction boundaries:** Application service methods own their transaction boundaries via `@Transactional`. Domain objects are transaction-agnostic.

### Domain Layer (`domain/`)

**Owns:** Business rules, aggregate invariants, domain events, port interface definitions.

**Does not own:** Any implementation detail — no SQL, no HTTP, no AI SDK calls.

**What lives here:**
- Aggregate roots and their state machine transitions
- Value objects (immutable records)
- Domain enums with business-meaningful methods
- Repository interfaces (ports — not implementations)
- Domain events

The domain layer is the most valuable and most stable part of the codebase. It changes only when business rules change — not when PostgreSQL upgrades, Spring AI changes API, or Weaviate releases a new version.

### Infrastructure Layer (`infrastructure/`)

**Owns:** All technical implementation details — database queries, vector store calls, LLM invocation, file storage, document parsing.

**Does not own:** Business logic. An infrastructure adapter is not allowed to make decisions that belong to the domain.

**Adapter types:**

| Adapter | Implements | Technology |
|---|---|---|
| `DocumentRepositoryAdapter` | `DocumentRepository` port | Spring Data JPA + PostgreSQL |
| `ChunkRepositoryAdapter` | `ChunkRepository` port | Spring Data JPA + PostgreSQL |
| `ConversationRepositoryAdapter` | `ConversationRepository` port | Spring Data JPA + PostgreSQL |
| `UserRepositoryAdapter` | `UserRepository` port | Spring Data JPA + PostgreSQL |
| `AuditLogRepositoryAdapter` | `AuditLogRepository` port | Spring Data JPA + PostgreSQL |
| `WeaviateVectorAdapter` | `VectorStorePort` | Weaviate Java client + Spring AI |
| `OllamaEmbeddingAdapter` | `EmbeddingPort` | Ollama + Spring AI |
| `OllamaLLMAdapter` | `LLMPort` | Ollama + Spring AI |
| `TikaDocumentParser` | `DocumentParserPort` | Apache Tika |
| `LocalFileStorageAdapter` | `FileStoragePort` | Local filesystem (swappable for S3) |

---

## Domain Model

### Aggregates

An aggregate is a cluster of domain objects treated as a single unit for data changes. Each aggregate root enforces its own invariants. Cross-aggregate references use IDs only — never object references.

```
Document (Aggregate Root)
│   id: DocumentId
│   tenantId: TenantId
│   ownerId: UserId
│   filename: String
│   format: SupportedFormat
│   status: DocumentStatus          ← State machine
│   metadata: DocumentMetadata      ← Value object
│   rawContentPath: String
│   chunkCount: int
│   ingestionError: String?
│
│   State transitions (domain-enforced):
│   PENDING → PARSING → CHUNKING → EMBEDDING → INDEXED
│                                             → FAILED (from any state)

Chunk (Aggregate Root)
│   id: ChunkId
│   documentId: DocumentId          ← Reference by ID only
│   tenantId: TenantId
│   sequenceNumber: int
│   content: String
│   metadata: ChunkMetadata         ← Value object
│   vectorId: String                ← Weaviate cross-reference

Conversation (Aggregate Root)
│   id: ConversationId
│   userId: UserId
│   tenantId: TenantId
│   title: String?
│   messages: List<Message>         ← Owned by aggregate
│
│   Message (Entity within Conversation)
│   │   id: UUID
│   │   role: MessageRole
│   │   content: String
│   │   citations: List<Citation>   ← Value objects
│   │   queryId: QueryId?

KnowledgeQuery (Aggregate Root)
│   id: QueryId
│   userId: UserId
│   tenantId: TenantId
│   conversationId: ConversationId?
│   originalText: String
│   rewrittenText: String?
│   filter: MetadataFilter          ← Value object
│   retrievedChunkIds: List<ChunkId>
│   latencyMs: Long?

User (Aggregate Root)
    id: UserId
    tenantId: TenantId
    email: String
    passwordHash: String
    active: boolean
    roles: Set<UserRole>
```

### Value Objects

Value objects are immutable. Equality is by content, not identity. All are implemented as Java 21 `record` types.

| Value Object | Fields | Purpose |
|---|---|---|
| `TenantId` | `UUID value` | Type-safe tenant reference |
| `UserId` | `UUID value` | Type-safe user reference |
| `DocumentId` | `UUID value` | Type-safe document reference |
| `ChunkId` | `UUID value` | Type-safe chunk reference |
| `ConversationId` | `UUID value` | Type-safe conversation reference |
| `QueryId` | `UUID value` | Type-safe query reference |
| `DocumentMetadata` | title, author, description, department, classification, tags | Document-level metadata |
| `ChunkMetadata` | pageNumber, sectionTitle, startOffset, endOffset, tokenCount, strategy | Chunk positional metadata |
| `MetadataFilter` | `Map<String, Object> criteria` | Pre-filter for vector search |
| `Citation` | chunkId, relevanceScore | Source attribution |
| `PageRequest` | pageNumber, pageSize | Framework-free pagination input |
| `PageResult<T>` | content, pageNumber, pageSize, totalElements | Framework-free pagination output |

**Why typed IDs instead of raw UUIDs?**

A method signature `deleteDocument(UUID documentId, UUID tenantId)` allows caller-side mistakes like passing arguments in the wrong order — the compiler cannot catch it. `deleteDocument(DocumentId, TenantId)` makes that mistake a compile error. On a platform with six entity types all identified by UUID, typed IDs eliminate an entire class of bugs.

---

## Port Contracts

Ports are interfaces in the domain layer. They define what the domain needs; adapters in infrastructure provide how it is done.

### Driven Ports (Out-Ports) — Infrastructure implements these

```
EmbeddingPort
    embed(List<String> texts): List<float[]>
    embed(String text): float[]

LLMPort
    generate(Prompt prompt): String
    generateStream(Prompt prompt): Flux<String>

VectorStorePort
    upsert(List<VectorDocument> documents): void
    hybridSearch(HybridQuery query): List<ScoredChunk>
    delete(List<String> vectorIds): void

DocumentParserPort
    parse(Path filePath, SupportedFormat format): ParsedDocument

FileStoragePort
    store(InputStream content, String filename): String   (returns path)
    load(String path): InputStream
    delete(String path): void

DocumentRepository
    save(Document): Document
    findById(DocumentId): Optional<Document>
    findByIdAndTenantId(DocumentId, TenantId): Optional<Document>
    findByTenantId(TenantId, PageRequest): PageResult<Document>
    findByOwnerIdAndTenantId(UserId, TenantId, PageRequest): PageResult<Document>
    softDelete(DocumentId): void

ChunkRepository
    saveAll(List<Chunk>): List<Chunk>
    findById(ChunkId): Optional<Chunk>
    findByIds(List<ChunkId>): List<Chunk>
    findByVectorId(String): Optional<Chunk>
    findByDocumentId(DocumentId): List<Chunk>
    deleteByDocumentId(DocumentId): void

ConversationRepository
    save(Conversation): Conversation
    findById(ConversationId): Optional<Conversation>
    findByIdAndUserId(ConversationId, UserId): Optional<Conversation>
    findByUserIdAndTenantId(UserId, TenantId, PageRequest): PageResult<Conversation>
    softDelete(ConversationId): void

UserRepository
    save(User): User
    findById(UserId): Optional<User>
    findByEmailAndTenantId(String, TenantId): Optional<User>
    existsByEmailAndTenantId(String, TenantId): boolean

AuditLogRepository
    save(AuditLog): void
    findByTenantId(TenantId, PageRequest): PageResult<AuditLog>
    findByUserId(UserId, PageRequest): PageResult<AuditLog>
```

### Driving Ports (In-Ports) — Application implements these

```
DocumentIngestionUseCase
    ingest(IngestDocumentCommand): IngestResult

QueryOrchestrationUseCase
    query(QueryCommand): QueryResult

ConversationUseCase
    createConversation(CreateConversationCommand): ConversationResult
    addMessage(AddMessageCommand): MessageResult
    getConversation(GetConversationQuery): ConversationResult
    deleteConversation(DeleteConversationCommand): void
```

---

## Data Flow

### Ingestion Flow

```
HTTP POST /api/v1/documents
    │
    ▼ [API Layer]
DocumentController.upload(MultipartFile, DocumentUploadRequest)
    → validates JWT, resolves tenantId
    → maps to IngestDocumentCommand
    │
    ▼ [Application Layer]
DocumentIngestionService.ingest(command)
    1. Create Document aggregate (status=PENDING)
    2. FileStoragePort.store(bytes) → rawContentPath
    3. Document.assignContentPath(path)
    4. Document.startParsing()  → status=PARSING
    5. DocumentParserPort.parse(path, format) → ParsedDocument
    6. Document.startChunking() → status=CHUNKING
    7. ChunkingService.chunk(text, format) → List<ChunkCandidate>
    8. Document.startEmbedding() → status=EMBEDDING
    9. EmbeddingPort.embed(List<String>) → List<float[]>
   10. VectorStorePort.upsert(chunks + vectors) → List<vectorId>
   11. Assign vectorIds to Chunk aggregates
   12. ChunkRepository.saveAll(chunks)
   13. Document.markIndexed(chunkCount) → status=INDEXED
   14. DocumentRepository.save(document)
   15. AuditService.log(DOCUMENT_UPLOADED)
    │
    ▼
202 Accepted with documentId
```

### Query and Generation Flow

```
HTTP POST /api/v1/query
    │
    ▼ [API Layer]
QueryController.query(QueryRequest)
    → validates JWT, resolves tenantId + userId
    → maps to QueryCommand
    │
    ▼ [Application Layer]
QueryOrchestrationService.query(command)
    1. Create KnowledgeQuery aggregate
    2. ConversationRepository.findById(conversationId) → history
    3. LLMPort.generate(rewritePrompt(originalQuery, history)) → rewrittenQuery
    4. KnowledgeQuery.setRewrittenText(rewrittenQuery)
    5. EmbeddingPort.embed(rewrittenQuery) → queryVector
    6. VectorStorePort.hybridSearch(queryVector, filter, topK=20) → List<ScoredChunk>
    7. ReRankingService.rerank(query, candidates) → top-5
    8. ChunkRepository.findByIds(chunkIds) → List<Chunk> (for citations)
    9. ContextAssemblyService.assemble(rankedChunks) → context string
   10. PromptAssemblyService.build(context, history, query) → Prompt
   11. LLMPort.generateStream(prompt) → Flux<String>
   12. SourceCitationService.extract(response, chunks) → List<Citation>
   13. Message assistantMessage = Message.assistantMessage(response, citations, queryId)
   14. Conversation.addMessage(userMessage)
   15. Conversation.addMessage(assistantMessage)
   16. ConversationRepository.save(conversation)
   17. KnowledgeQuery.recordRetrieval(chunkIds, latencyMs)
   18. QueryRepository.save(query)
   19. AuditService.log(QUERY_EXECUTED)
    │
    ▼
SSE stream of response chunks, followed by citations payload
```

---

## Multi-Tenancy Model

Tenant isolation operates at three levels simultaneously:

### Level 1: Application (JWT claim)

Every authenticated request carries a `tid` (tenant ID) claim in the JWT. The `JwtAuthenticationFilter` resolves this to a `TenantId` value object and stores it in the security context. Every service method that accesses data receives `TenantId` as a parameter — it is never assumed or derived from other context.

### Level 2: Database (tenant_id column + RLS)

Every query-facing table (`documents`, `chunks`, `conversations`, `messages`, `queries`, `audit_logs`) has a `tenant_id` column. PostgreSQL Row Level Security policies enforce `tenant_id = current_setting('app.tenant_id')` on all reads and writes. The application sets `app.tenant_id` at the start of each database session.

This is a defence-in-depth measure. Even if the application layer had a bug that passed the wrong `TenantId`, the database would reject the query.

### Level 3: Vector Store (Weaviate native tenancy)

Weaviate's native multi-tenancy creates a physical tenant per organisation. Every vector operation — insert, search, delete — is scoped to the tenant. Tenant A's vectors are physically unreachable from Tenant B's queries, regardless of the query parameters.

This combination provides defence at three independent layers. Bypassing any one layer still leaves two others intact.

---

## Event Model

Domain events represent facts that occurred in the domain. They are produced by aggregates and consumed by cross-cutting services (audit, notifications, analytics).

| Event | Produced by | Consumed by |
|---|---|---|
| `DocumentIngested` | `Document.markIndexed()` | `AuditService`, future analytics pipeline |
| `DocumentFailed` | `Document.markFailed()` | `AuditService`, future notification service |
| `QueryExecuted` | `KnowledgeQuery.recordRetrieval()` | `AuditService`, query analytics |
| `ConversationCreated` | `Conversation.create()` | `AuditService` |
| `UserCreated` | `User.create()` | `AuditService` |

Events are published via a lightweight `EventPublisher` port. The initial implementation is synchronous (Spring's `ApplicationEventPublisher`). The port abstraction allows the publisher to be swapped for Kafka, RabbitMQ, or Amazon EventBridge when asynchronous event processing is required.

---

## Pagination Contract

The domain defines its own pagination types to avoid importing `org.springframework.data.domain.Pageable` into the domain layer.

**`PageRequest`** — Input from the caller (framework-free):

```
pageNumber: int    (zero-based)
pageSize:   int    (1–200, validated)
offset():   int    (computed: pageNumber × pageSize)
```

**`PageResult<T>`** — Output to the caller (framework-free):

```
content:       List<T>
pageNumber:    int
pageSize:      int
totalElements: long
hasNext():     boolean   (computed)
totalPages():  int       (computed)
```

Infrastructure adapters translate between `PageRequest`/`PageResult` and Spring Data's `Pageable`/`Page` at the port boundary. No `Pageable` crosses into the application or domain layers.
