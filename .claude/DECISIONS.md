# Frozen Architecture Decisions

Architecture

- Hexagonal Architecture
- Domain Driven Design
- Modular Monolith

Technology

- Java 21
- Spring Boot
- Spring AI
- PostgreSQL
- Flyway
- Weaviate
- Ollama

Retrieval

- Hybrid Retrieval
- BM25
- Vector Search
- Reciprocal Rank Fusion
- Query Rewriting

Principles

- Provider Independence
- Single Embedding Pipeline
- Incremental Development
- Semantic Versioning
- Production Quality
- Documentation First
- Test First Mindset

---

# P04.10 ADRs — Conversation Memory

ADR M01: ConversationHistoryPort is read-only

Decision: ConversationHistoryPort provides only getRecentMessages(). Writing conversation history is a separate concern handled by ConversationRepository or a future write port.
Rationale: SRP — reading for prompt augmentation and persisting new exchanges are different responsibilities. Mixing them into one port couples generation to persistence lifecycle.

ADR M02: conversationId is optional on GenerationRequest (null = stateless)

Decision: GenerationRequest.conversationId() may be null. Null means generation proceeds without memory.
Rationale: Not all generation requests belong to a conversation (batch queries, one-shot API calls). Forcing a conversationId would break stateless use cases.

ADR M03: Memory window size is an application-level config, not per-request

Decision: app.conversation.memory-window-size controls how many recent messages are fetched. It is injected into GenerationService via @Value, not supplied by callers.
Rationale: Window size affects token budget and is an operational concern. Per-request control is a future extension if needed.

ADR M04: InMemoryConversationHistoryAdapter is the P04.10 seam

Decision: The only ConversationHistoryPort implementation in P04.10 is InMemoryConversationHistoryAdapter. It is explicitly designed to be replaced by PostgreSQL, Redis, MongoDB, or an external service without changing domain or application.
Rationale: Durable persistence for conversation memory is deferred. The port contract ensures replaceability.

---

# P04.11 ADRs — Citation Engine

ADR C01: Marker parsing uses a hand-written scan, not a regular expression

Decision: PositionalCitationAdapter locates `[SOURCE:N]` markers with a left-to-right scan for the literal prefix `[SOURCE:` and the next `]`, not a regex pattern.
Rationale: LLM output is untrusted input; a regex over unbounded, adversarial text risks catastrophic backtracking. A manual scan has predictable linear-time behaviour and makes malformed-input handling (missing bracket, non-digit index) explicit code paths instead of pattern edge cases.

ADR C02: Chunk resolution keys off AssembledChunk.position(), not list index

Decision: PositionalCitationAdapter builds a `position -> AssembledChunk` map from `AssembledContext.chunks()` and resolves each marker against that map, rather than indexing directly into the list.
Rationale: Decouples citation resolution from an implicit assumption that the list is sorted by position. Correct today and safe if a future AssembledContext producer ever returns chunks in a different order.

ADR C03: Duplicate marker references are deduplicated by chunkId, preserving first-occurrence order

Decision: When the same `[SOURCE:N]` marker (or two markers resolving to the same chunk) appears more than once in the generated text, only one Citation is produced, positioned at its first appearance.
Rationale: A citation list represents the distinct sources referenced in an answer, not a tally of how many times each was mentioned. Repetition strengthens confidence in one source; it does not introduce a second source.

ADR C04: Malformed or out-of-range markers are silently ignored, never thrown

Decision: PositionalCitationAdapter never raises an exception for a marker it cannot resolve — empty index, non-digit index, missing closing bracket, zero/negative index, numeric overflow, or an index with no matching chunk are all skipped, and scanning continues.
Rationale: Generation must always complete. LLM output is not guaranteed to be well-formed; citation resolution is a best-effort enrichment step and must degrade gracefully rather than fail the whole response.

ADR C05: PositionalCitationAdapter replaces PassthroughCitationAdapter as the sole CitationPort implementation

Decision: The P04.9/P04.10 seam (`PassthroughCitationAdapter`, always `List.of()`) is removed outright and replaced by `PositionalCitationAdapter`. There is no dual-adapter transition period.
Rationale: The seam's stated purpose (documented in its own Javadoc) was to keep `CitationPort` wired end-to-end until P04.11 shipped. Keeping both would leave an unused, misleading implementation in the codebase with no call site ever selecting it.

---

# P04.12 ADRs — Enterprise Output Guardrails

ADR GR01: OutputGuardrailsPort signature stays frozen (ADR G16); finish-reason-aware blocking is deferred

Decision: `PolicyBasedOutputGuardrailsAdapter` implements all P04.12 policy using only the `generatedText` the port already receives. `FinishReason` is not added to `OutputGuardrailsPort.apply()` in this milestone; ADR G16 ("receives text only") is left exactly as written.
Rationale: `FinishReason` is a closed five-value enum, and `OllamaLlmAdapter` already defensively maps any unrecognized provider string to `STOP` — there is no "invalid" value that can structurally reach this layer. The only real use of finish-reason-aware guardrails is a business rule (e.g. treat `ERROR` as blocking), which is a genuine future capability, not a gap in this milestone. Widening the port for a rule nobody has specified yet would be a premature signature change. Deferred as explicit technical debt (see CHANGELOG) rather than implemented via a workaround that reads the spirit of G16 narrowly.

ADR GR02: Null and blank generated text are policy violations resolved in-band, never thrown

Decision: `generatedText == null`, empty, or whitespace-only (including text that becomes whitespace-only after control-character stripping) all resolve to `GuardrailResult.block(SAFE_FALLBACK_TEXT)`. `PolicyBasedOutputGuardrailsAdapter.apply()` never throws for malformed or absent output. Only `tenantId == null` throws `NullPointerException` — a caller-contract violation, not output formatting, consistent with every other port in the codebase.
Rationale: Generation must always complete (mirrors ADR C04 for citations). A missing or empty LLM response is exactly the kind of provider misbehavior guardrails exist to absorb, not propagate as an exception that would fail the whole request.

ADR GR03: Oversized responses are truncated and passed, not blocked

Decision: Responses exceeding `app.guardrails.max-response-length` (default 8192 characters) are truncated to that limit and returned as `GuardrailResult.pass(truncatedText)`. Length alone is never a blocking condition.
Rationale: An overlong-but-otherwise-valid answer is not unsafe content — capping length is an operational/token-budget concern, not a policy rejection. Reusing `PASS` avoids inventing a third `GuardrailStatus` value that no caller currently needs.

ADR GR04: Malformed-output normalisation strips non-printable control characters only

Decision: `PolicyBasedOutputGuardrailsAdapter` removes Unicode control characters other than `\r`, `\n`, `\t` before any blank or length check runs. It does not attempt HTML/Markdown sanitisation, PII redaction, or any semantic validation.
Rationale: Those are explicitly out of scope for this milestone. Control-character stripping is the narrowest deterministic, provider-independent interpretation of "malformed output" that is testable without building a classifier — consistent with "this milestone is not AI moderation."

ADR GR05: PolicyBasedOutputGuardrailsAdapter replaces PassthroughOutputGuardrailsAdapter as the sole OutputGuardrailsPort implementation

Decision: The P04.9 seam (`PassthroughOutputGuardrailsAdapter`, always `GuardrailResult.pass(text)`) is removed outright and replaced by `PolicyBasedOutputGuardrailsAdapter`. There is no dual-adapter transition period.
Rationale: Mirrors ADR C05's precedent for `CitationPort` — the seam's stated purpose was to keep `OutputGuardrailsPort` wired end-to-end until this milestone shipped; keeping both would leave an unused, misleading implementation with no call site ever selecting it.

---

# P04.13 ADRs — Architecture Reconciliation

ADR R01: PersistentConversationHistoryAdapter replaces InMemoryConversationHistoryAdapter as the sole ConversationHistoryPort implementation

Decision: ConversationHistoryPort is now implemented by PersistentConversationHistoryAdapter, which reads from the same ConversationRepository that ConversationApplicationService writes to (via Conversation.recentMessages(int), an existing aggregate method). The P04.10 in-memory seam is removed outright — no dual-adapter transition period, consistent with the C05/GR05 precedent. ConversationHistoryPort and GenerationService are unchanged.
Rationale: Fulfils exactly what ADR M04 predicted ("explicitly designed to be replaced... without changing domain or application"). The architecture audit found the in-memory adapter's addMessage() was called only from its own test — ConversationApplicationService.addUserMessage() persists to ConversationRepository exclusively, so generation-time memory was always empty in real usage despite P04.10 being marked complete. ConversationRepository.findById(ConversationId) is not tenant-scoped at the query level (its existing callers separately verify ownership via findByIdAndUserId), so the new adapter independently checks conversation.getTenantId().equals(tenantId) after fetch and treats a mismatch as "not found" — never leaking another tenant's history.

ADR R02: OllamaLlmAdapter now retries transient failures, reconciling ADR G13

Decision: OllamaLlmAdapter.generate() retries ChatModel.call() up to 3 times with exponential backoff (100ms initial, capped at 1000ms) before wrapping the failure as LlmProviderUnavailableException. This mirrors the existing retry shape in EmbeddingService.embedWithRetry elsewhere in the codebase — a plain bounded loop, not a resilience framework. Only the provider call is retried; prompt construction is pure and retrying it has no value.
Rationale: The architecture audit found ADR G13 ("Retry logic belongs inside the provider adapter, not GenerationService") was contradicted by the actual adapter, which had no retry at all. G13 itself is left untouched (append-only); this ADR records how it is now actually satisfied.

ADR R03: A missing roles.created_at column was a real, previously-undetected schema/entity drift — fixed via migration, not documentation

Decision: V017__add_roles_created_at.sql adds the created_at column RoleEntity (which extends BaseUuidEntity) has always required. The RoleEntity/V002 mismatch is not treated as a documented limitation — it is fixed outright.
Rationale: Discovered while adding the P04.13.4 baseline persistence test suite: the very first test that actually booted a full JPA context against a real (Testcontainers) database failed Hibernate schema validation on this table. No prior test in the codebase had ever done so. This directly corroborates the audit's "zero test coverage on the persistence layer" finding — it wasn't just a coverage gap, a real schema bug was hiding behind it. Left unfixed, the baseline test suite this milestone requires could not run at all.

ADR R04: spring.flyway.clean-on-validation-error removed from the test profile — dead/rejected configuration, not a behavioural change

Decision: The test Spring profile's flyway.clean-on-validation-error: true is deleted from application.yml. This property has been removed from the Flyway/Spring Boot version this project depends on (Spring Boot 3.5.0) and its presence caused a hard BeanCreationException at context startup, before Flyway even ran.
Rationale: Same discovery path as ADR R03 — no test had ever booted a full Spring context with the test profile active, so this had never surfaced. Removing it (rather than substituting an equivalent) is correct: Testcontainers already provisions a fresh, disposable database per test run, so "auto-clean on validation failure" protects nothing here that ephemeral containers don't already guarantee, and validate-on-migrate: true (inherited from the default profile) still enforces schema correctness.

ADR R05: --enable-preview removed from the Gradle build

Decision: The compiler, test, and bootRun --enable-preview JVM/compiler arguments are removed from build.gradle.
Rationale: A repository-wide search (string templates, unnamed patterns/variables, structured concurrency — the only preview features in Java 21) found zero usages anywhere in the codebase. --enable-preview was pure unforced risk: it locks every build to the exact JDK 21 feature-release forever (preview-compiled class files carry a special version marker only that exact release can run) and is explicitly not supported for production use by the JDK vendor. gradle clean compileJava compileTestJava succeeds identically without it, confirming no functional dependency existed.

---

# P05.1 ADRs — End-to-End RAG Orchestration & REST Exposure

ADR O01: RagOrchestrationService is the sole cross-cutting coordinator; existing services are unmodified

Decision: A new application.orchestration.RagOrchestrationService coordinates ConversationApplicationService, RetrievalService, ContextAssemblyPort, and GenerationService for one full RAG turn. It contains no business logic of its own — each collaborator call is justified by exactly one sentence in the class Javadoc. None of the four collaborators were changed to support this; RagOrchestrationService is purely additive composition.
Rationale: Retrieval, generation, and conversation persistence are each already owned by a dedicated, independently-tested service. Folding turn-level coordination into any one of them would blur that ownership (e.g. GenerationService gaining a ConversationApplicationService dependency would conflate "produce a response" with "manage conversation lifecycle"). A dedicated coordinator keeps each existing service's responsibility exactly as narrow as it already was, and gives the one new cross-cutting concern (the turn sequence itself) an honest, singular home instead of hiding it inside whichever service happened to be modified first.

ADR O02: Assistant reply persistence is symmetrical with user message persistence, not a new pattern

Decision: ConversationApplicationService.addAssistantMessage(AddAssistantMessageCommand) mirrors the existing addUserMessage(AddUserMessageCommand) exactly — same findByIdAndUserId ownership check, same Conversation.addMessage aggregate mutation, same conversationRepository.save, same MessageAddedEvent publication, differing only in MessageRole and the citation payload Message.assistantMessage already accepts. ConversationRepository, ConversationHistoryPort, and the Conversation aggregate itself are unchanged.
Rationale: Closes the specific gap the P04.13 reconciliation identified and deliberately deferred (assistant replies were never persisted). Reusing the exact shape of an already-correct, already-tested method is lower-risk than inventing a new persistence pattern, and keeps ConversationApplicationService's two write paths (user turn, assistant turn) trivially easy to reason about together.

ADR O03: REST API is versioned by URL prefix; errors are RFC 7807 Problem Details via one centralized handler

Decision: All endpoints live under /api/v1 (version in the path, not a header or content-type parameter). A single api.exception.GlobalExceptionHandler (@RestControllerAdvice) maps every exception type to a Spring-native ProblemDetail: ResourceNotFoundException to 404, DuplicateResourceException to 409, IllegalArgumentException and bean-validation failures to 400, GenerationException/RetrievalException/LlmException to 502 (upstream pipeline failure), anything else to a generic 500. No exception message that could carry generated text, prompt content, or user query text is included in a response body, matching the project-wide logging policy.
Rationale: URL-path versioning is the simplest scheme to reason about and to route on, and defers any harder versioning decision (header-based negotiation, etc.) until there's a second version to justify it. RFC 7807 needed no new dependency — Spring Boot 3.5's org.springframework.http.ProblemDetail is a first-class type. Centralizing exception mapping in one class (rather than per-controller try/catch) is what keeps controllers thin, per this milestone's explicit constraint.

ADR O04: RetrievalResult now carries the effective (post-rewrite) query text

Decision: RetrievalResult gained a third field, effectiveQueryText, populated by each RetrievalPort adapter (HybridRetrievalAdapter, WeaviateRetrievalAdapter, PostgresBm25RetrievalAdapter) from the queryText parameter they already receive, and threaded through by RetrievalService's own ranked-result branch. RetrievalPort's interface signature is unchanged.
Rationale: ContextAssemblyPort.assemble(chunks, queryText, tokenBudget) requires the rewritten query text — AssembledContext.queryText's own Javadoc has always documented it as "the effective query text after rewriting (mirrors what retrieval used)". Before this milestone nothing needed that value outside RetrievalService, so it was computed and discarded. RagOrchestrationService is the first caller that needs it without re-deriving it — and re-deriving it (calling QueryRewritePort a second time) was rejected: OllamaQueryRewriteAdapter is LLM-backed, so a second call risks returning a different rewrite than the one retrieval actually used, silently breaking AssembledContext's documented contract. This mirrors the precedent already set when ContextAssemblyPort itself evolved from returning String to returning AssembledContext for the same reason (exposing data a caller needs that was previously discarded).

ADR O05: Temporary permissive SecurityFilterChain — see api.config.SecurityConfig's own Javadoc

Decision: A single SecurityFilterChain bean permits every request and disables CSRF (the API is stateless — no session, no cookies). It exists specifically to prevent Spring Boot's default security auto-configuration (a generated password, HTTP Basic on every route) from silently activating now that spring-boot-starter-security has its first consuming controller. tenantId/userId travel as explicit fields on every P05.1 request DTO rather than being derived from an authenticated principal, because there is no authentication context to derive them from yet.
Rationale: P05.2 (Authentication Foundation) replaces this class outright with real JWT validation — not layered on top of it. Shipping the REST layer without this bean was not an option: the classpath trap is real and immediate the moment the first @RestController exists, per the Phase 5 roadmap's own risk analysis.
