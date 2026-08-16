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

---

# P05.2 ADRs — Authentication Foundation

ADR A01: HS256 (HMAC-SHA256) signs and verifies access tokens; RS256 is deferred

Decision: `JwtTokenProvider` signs and verifies tokens with a single symmetric key (HMAC-SHA256) derived from `security.jwt.secret-key`. RS256 (asymmetric key pair) — the target described in `docs/roadmap.md`'s Phase 5 "Enterprise Hardening" aspiration — is not implemented in this milestone.
Rationale: `application.yml` already scaffolded `security.jwt.secret-key` as a single string ahead of this milestone, matching HS256's shape, not a key pair. HMAC-SHA256 needs no additional key-management infrastructure (PEM key pairs, a JWKS endpoint, key rotation) that an asymmetric scheme would require, and nothing in P05.2's scope needs one: verification happens only inside this same monolith that signs the tokens. Introducing a key pair now, with no second service that needs to verify tokens independently, would be a speculative abstraction ahead of any real consumer. RS256 remains a legitimate future upgrade if/when an external verifying service appears — `docs/roadmap.md`'s own stated rationale for it.

ADR A02: Login issues an access token only; refresh tokens are deferred

Decision: `POST /api/v1/auth/login` authenticates email + password + tenantId and returns a single short-lived access token (`security.jwt.access-token-expiry-ms`, default 15 minutes). There is no `/api/v1/auth/refresh` or `/api/v1/auth/logout` endpoint, and `security.jwt.refresh-token-expiry-ms` (already scaffolded in `application.yml`) remains unused.
Rationale: The milestone is explicitly scoped to "Authentication Foundation," excluding "Operational hardening." Refresh-token rotation, revocation-on-logout, and reuse detection (`docs/roadmap.md`'s stated target design) are session-lifecycle/operational concerns, not "prove who's calling" concerns. Shipping only access-token issuance keeps this milestone's surface to exactly what every other endpoint needs to require authentication.

ADR A03: `AuthenticateUserUseCase` verifies identity in `application.user`; only `api.security.JwtTokenProvider` ever produces a JWT

Decision: `AuthenticateUserUseCase` (new, `application.user`) checks email/password/tenantId/active-flag and returns the domain `User` or throws `InvalidCredentialsException`. `api.security.JwtTokenProvider` is the only class that turns a verified identity into a signed token. `AuthController` is the sole caller of both, always in that order.
Rationale: Keeps the application layer technology-agnostic, matching every other use case in this codebase (none of them know about HTTP, JWT, or any other delivery mechanism) and satisfying the ArchUnit-enforced rule that `application` must never depend on `api`. "Is this a valid login" and "mint a token for it" are different responsibilities — the first is a business rule, the second is a delivery-layer encoding of that rule's outcome.

ADR A04: `JwtAuthenticationFilter` never rejects a request itself; only `RestAuthenticationEntryPoint` produces a 401

Decision: On any missing, malformed, or invalid bearer token, `JwtAuthenticationFilter` clears the `SecurityContext` and continues the filter chain — it never writes a response or throws. `RestAuthenticationEntryPoint`, invoked automatically by Spring Security's authorization stage only when an unauthenticated request reaches an endpoint that actually requires authentication, is the single place a 401 `ProblemDetail` is ever produced.
Rationale: A filter that short-circuits on a bad token would incorrectly reject requests to `permitAll` endpoints (login, health, Swagger) just because a stale or garbage token happened to be attached. Delegating the accept/reject decision entirely to the authorization stage keeps exactly one component responsible for turning "unauthenticated" into an HTTP response — the same "one centralized handler" precedent `GlobalExceptionHandler` set for business exceptions (ADR O03).

ADR A05: `tenantId`/`userId` are removed from `CreateConversationRequest`, `SendMessageRequest`, and the `GET .../{id}` `userId` query param — both now come exclusively from the validated JWT

Decision: The three P05.1 request shapes that carried explicit `tenantId`/`userId` fields no longer do. `ConversationController` reads both from the `JwtAuthenticationToken` Spring Security populates from the incoming Bearer token.
Rationale: This is exactly what ADR O05 committed to: "P05.2 replaces both with values extracted from the validated JWT." Leaving the fields in place after real authentication exists would let any caller impersonate any tenant or user simply by editing the request body — the opposite of what an authentication milestone exists to prevent.

ADR A06: `AuthenticateUserUseCase` always invokes `PasswordEncoder.matches()`, even for an unknown email, against a fixed dummy hash

Decision: When no user is found for the given email/tenant, `AuthenticateUserUseCase` still calls `passwordEncoder.matches(rawPassword, DUMMY_PASSWORD_HASH)` before throwing `InvalidCredentialsException` — it never short-circuits straight to the exception.
Rationale: Found during this milestone's own mandatory self-review (CLAUDE.md's "Security concerns" review criterion). Without this, an unknown-email request would skip BCrypt's deliberately expensive hash comparison entirely, while a known-email/wrong-password request would still pay that cost — a measurable timing difference that lets an attacker enumerate valid emails/tenants even though both paths throw the byte-for-byte identical exception (`InvalidCredentialsException`'s whole stated purpose, per its own Javadoc). Comparing against a fixed, meaningless-but-valid BCrypt hash equalizes the cost across every failure path with no behavioral change to the success path.

---

# P05.3 ADRs — Tenant & Role Authorization

**Scope note:** This milestone builds the tenant/role/ownership *authorization boundary* at the REST layer. CLAUDE.md's target-architecture diagram positions an "Authorization Filter" between Query Rewrite and Hybrid Retrieval — that is the separate, still-unbuilt "Full Authorization Filter (fine-grained, metadata-based content filtering)" item `ROADMAP.md` lists under Future, integrated into the retrieval pipeline itself. P05.3 does not claim to be that. `RetrievalService`/`GenerationService` already thread `TenantId` through retrieval and guardrails (pre-dating this milestone), which is as far as "metadata authorization... consistency across retrieval, citations" reaches today — no retrieval-pipeline code changed in this milestone.

ADR AZ01: `AuthorizationInterceptor` (a `HandlerInterceptor`, not a `Filter`) is the sole role-authorization decision point; it never re-validates ownership

Decision: A new `api.security.AuthorizationInterceptor`, registered via `api.config.WebMvcConfig` against every `/api/v1/**` route, reads a new `@RequireRole` annotation off the target controller method and denies the request (`AccessDeniedException`) if the authenticated principal's roles don't intersect. It answers only "does this role permit this operation" — never "does this caller own this specific resource." It is implemented as a `HandlerInterceptor` rather than a `Filter` (the shape `JwtAuthenticationFilter` uses) specifically because a `Filter` runs before Spring MVC resolves the target handler method, so it has no clean way to read a method-level annotation or path variable; a `HandlerInterceptor` runs after handler resolution, with the `SecurityContext` already populated by `JwtAuthenticationFilter`.
Rationale: The milestone brief warns against "duplicated authorization checks." Ownership validation for a conversation requires loading that conversation — `ConversationApplicationService` already does exactly that, via `findByIdAndUserId`, at the point it needs the data anyway. Having `AuthorizationInterceptor` *also* fetch the conversation to pre-validate ownership would mean querying the same repository twice per request for no additional safety, plus a second place that could drift out of sync with the first. Keeping ownership at the data-access site (ADR OW01) and role-checking at the boundary (this ADR) is the only split that avoids duplication while still giving the milestone a single, named "authorization boundary" component for the dimension (role) that genuinely doesn't need resource data to decide.

ADR AZ02: Role-to-action policy — `VIEWER`/`AUDITOR` are read-only; `USER`/`ADMIN` may also create conversations and send messages

Decision: `ConversationController.createConversation` and `.sendMessage` are annotated `@RequireRole({UserRole.USER, UserRole.ADMIN})`. `getConversation` carries no `@RequireRole` — any authenticated role may call it (ownership still applies, per ADR OW01).
Rationale: No prior milestone defined which of the four existing `UserRole` values (`ADMIN`, `USER`, `VIEWER`, `AUDITOR`) may perform which action — this is a new business rule this milestone had to introduce to have anything to enforce. `VIEWER` and `AUDITOR` are read-oriented by name and by every other system's convention for those role names; mapping them to read-only and reserving mutation for `USER`/`ADMIN` is the most direct reading of the existing four-value enum without inventing a fifth concept (a permission matrix, scopes, etc. — explicitly out of scope: "do not invent unnecessary permission frameworks"). **This is a judgment call made in the absence of a prior specification** — flagged here and in the milestone completion report specifically so it's easy to find and override if the intended policy differs.

ADR AZ03: `AccessDeniedException` is handled by `GlobalExceptionHandler`, not a dedicated Spring Security `AccessDeniedHandler`; `ADMIN` does not bypass ownership

Decision: `GlobalExceptionHandler` gained one handler, `@ExceptionHandler(AccessDeniedException.class)` → 403, with a message that never distinguishes "wrong role" from "not your resource." No `AccessDeniedHandler` bean was added to `SecurityConfig`. Separately: no role, including `ADMIN`, bypasses the ownership check in ADR OW01 — `ADMIN` currently means "may create/send," not "may read anyone's conversation."
Rationale: An exception thrown by a `HandlerInterceptor.preHandle()` propagates through Spring MVC's own exception-resolution chain (the same one that backs `@ExceptionHandler`) before it would ever reach Spring Security's filter-chain-level `ExceptionTranslationFilter` — so a security-layer `AccessDeniedHandler` would either never fire (dead code) or create two competing places that decide a 403's shape, contradicting ADR O03's single-centralized-handler precedent. On the `ADMIN` bypass question: nothing in this milestone's brief asked for cross-user read access for any role, and building it would be a genuine, security-sensitive new capability deserving its own explicit design and test coverage — not a side effect of a role-mapping ADR. Deliberately deferred, not forgotten (see Future Considerations).

ADR TN01: Tenant isolation is a defensive, explicit post-fetch check — extended from the existing ADR R01 pattern, not a new mechanism

Decision: `ConversationApplicationService.getConversation`, `.addUserMessage`, and `.addAssistantMessage` now each take a `TenantId` and, after the existing `findByIdAndUserId` ownership-scoped fetch succeeds, call a new private `requireTenantMatch` helper that throws `ResourceNotFoundException` (never a distinct "forbidden" outcome) on a mismatch. `AddUserMessageCommand`/`AddAssistantMessageCommand` gained a `tenantId` field; `RagOrchestrationService` and `ConversationController` supply it from data they already have (`SendMessageCommand.tenantId()` / the JWT). `.renameConversation`/`.deleteConversation` were **not** changed — see Technical Debt in the completion report.
Rationale: `ConversationRepository.findByIdAndUserId` has never been tenant-scoped at the query level; tenant isolation for conversations has always relied entirely on the invariant "a `UserId` belongs to exactly one `TenantId`, forever" holding everywhere else in the system. That invariant happens to hold today, but it is implicit, not verified at this boundary — exactly the gap ADR R01 already found and fixed once before, in `PersistentConversationHistoryAdapter`, for the same aggregate. This ADR applies that identical, already-precedented fix to the three REST-reachable mutation/read paths, rather than inventing a new tenant-scoping mechanism (e.g., changing the repository query signature itself, which would be a larger, riskier change for no additional real-world safety given the invariant above).

ADR OW01: Resource ownership validation stays exactly where it already was — at the point of fetch in `ConversationApplicationService` — and continues to resolve to 404, never 403

Decision: No new ownership-check component was introduced. `findByIdAndUserId` (pre-dating this milestone) remains the sole ownership gate for conversations; a caller who is authenticated, has the right role, but doesn't own the target conversation receives the identical `ResourceNotFoundException` → 404 a caller requesting a genuinely nonexistent ID would receive.
Rationale: This was already correct, tested (indirectly) behavior — P05.3's job was to make it *explicit and verified*, not to replace it. Returning 404 rather than 403 for "exists but isn't yours" is a deliberate anti-enumeration choice (a 403 would confirm the ID is real) — consistent with ADR A06's timing-leak fix and `InvalidCredentialsException`'s design in P05.2: this codebase's established pattern is that authorization failures never leak more information than the boundary equivalent of "not found."

---

# P05.4 ADRs — Observability Foundation

**Scope note:** "Provide production-grade observability" here means instrumenting what already exists (REST, security, orchestration, retrieval, generation), not building a monitoring platform. Distributed tracing backends, Prometheus/Grafana deployment, and ELK/OpenSearch/Splunk are explicitly out of scope per the milestone brief — this phase makes the application *observable*; operating that observability stack is a deployment concern for later.

ADR OB01: Spring Boot Actuator + Micrometer, with no new dependency — `/actuator/health` and `/actuator/info` are public; `/actuator/metrics` and `/actuator/prometheus` stay behind the existing JWT boundary

Decision: `spring-boot-starter-actuator` and `micrometer-registry-prometheus` were already project dependencies (scaffolded ahead of this milestone, mirroring the pattern P05.2's JWT dependencies were scaffolded ahead of P05.2). No new dependency was added for metrics/observability — `io.micrometer:micrometer-observation` classes used here (ADR OB02) are already transitively present via `micrometer-core`. `SecurityConfig.PUBLIC_ENDPOINTS` gained `/actuator/info` alongside the pre-existing `/actuator/health`; `/actuator/metrics` and `/actuator/prometheus` were deliberately **not** added to that list — they remain reachable only with a valid JWT, exactly as they already were before this milestone (an accidental byproduct of P05.2's narrower `permitAll` list, now a deliberate choice).
Rationale: "Expose only endpoints appropriate for the project... avoid exposing unnecessary operational information" is the milestone's own instruction. `/actuator/health` (liveness/readiness) and `/actuator/info` (static app identity) reveal nothing sensitive and are exactly what a load balancer or support engineer needs without authenticating. Metrics can reveal traffic volume, error rates, and auth-failure counts — real operational intelligence an unauthenticated caller on the public internet shouldn't get for free. A separate, network-isolated management port (`management.server.port`) is the standard production pattern for letting an internal-only Prometheus scrape without a token — but stood up correctly it also changes deployment topology (`docker-compose.yml`, health-check URLs, README instructions), which is an operational-hardening concern this milestone's brief explicitly excludes. Requiring a JWT for `/actuator/metrics`/`/actuator/prometheus` in the meantime is a safe, zero-new-surface default; a service-account token or a dedicated management port remain legitimate future upgrades (see Future Considerations).

ADR OB02: Latency is instrumented via the Micrometer Observation API (not hand-rolled Timers); failure counters exist only where Spring Boot doesn't already provide the signal; no metric is ever tagged by a raw tenant/user/conversation ID

Decision: `RetrievalService.retrieve()`, `GenerationService.generate()`, and `RagOrchestrationService.handleUserMessage()` each run their existing body inside `Observation.createNotStarted("eka.retrieval"|"eka.generation"|"eka.orchestration", observationRegistry).observe(...)` — an extraction of the existing method body into a private method, no logic changed. Two new `Counter`s exist: `eka.auth.failures` (tag `type`: `token` from `JwtAuthenticationFilter`, `credentials` from `AuthenticateUserUseCase`) and `eka.authz.failures` (tag `reason`: `role` from `AuthorizationInterceptor`, `ownership` from `ConversationApplicationService`'s tenant-mismatch branch — the plain "not found" branch, which cannot cheaply distinguish "doesn't exist" from "exists, wrong owner" without a second query, is not instrumented). Request counts, response times, and error counts are **not** separately instrumented — Spring Boot's auto-configured `http.server.requests` timer (active by default whenever Actuator + a `MeterRegistry` are on the classpath) already provides these, tagged by status/outcome/URI. No custom metric anywhere carries a `tenantId`, `userId`, or `conversationId` as a tag value.
Rationale: An `Observation` is strictly more than a hand-rolled `Timer.record(...)`: today it produces the same Micrometer timer (via Spring Boot's auto-registered `DefaultMeterObservationHandler`), and the moment `micrometer-tracing` + a tracer bridge are added in a future milestone, the exact same call sites start producing spans with zero code changes — directly satisfying "prepare the application for future distributed tracing without implementing it." Duplicating `http.server.requests` with a custom request counter would be exactly the "duplicated metrics" the quality gate forbids. Raw IDs as tag values are a well-known Micrometer/Prometheus anti-pattern — each distinct value becomes a permanent time series, and a UUID has effectively unbounded cardinality, which degrades or crashes a real metrics backend over time; tenant/user/conversation context belongs in logs and correlation IDs (ADR OB03/OB04), which don't have this constraint, not in metric tags.

ADR OB03: A single `CorrelationIdFilter`, registered before every other filter including Spring Security's own first filter; the ID is echoed on the response header only, never embedded in `ProblemDetail` bodies; unsafe caller-supplied IDs are replaced, not trusted

Decision: `api.observability.CorrelationIdFilter` reads `X-Correlation-Id` if present and safe (`^[A-Za-z0-9._-]{1,64}$`), else generates a UUID; puts it in MDC for the duration of the request (cleared in `finally`); echoes it on the response header. `SecurityConfig` registers it via `.addFilterBefore(correlationIdFilter, DisableEncodeUrlFilter.class)` — ahead of `DisableEncodeUrlFilter`, Spring Security's own first filter, not just ahead of `JwtAuthenticationFilter`. `GlobalExceptionHandler` was **not** touched to add a `correlationId` property to `ProblemDetail` bodies.
Rationale: Registering before Spring Security's own chain (not just before `JwtAuthenticationFilter`) means every response — including a 401 from an unauthenticated request, or anything Spring Security itself rejects — still carries a correlation ID; registering later would leave exactly the failure responses that most need one uncorrelated. The safe-pattern check exists because the header is untrusted client input that flows straight into every log line for the request: without it, a client could inject CRLF sequences to forge fake log entries (log injection) or supply an unbounded string. Response-header-only propagation (rather than also patching all seven `GlobalExceptionHandler` handlers to add a `ProblemDetail` property) was chosen because it's the same one code path Spring Boot already handles for the caller-facing side (an HTTP header, no framework-specific per-handler code) and covers every response — success and error alike — not just the exception-mapped ones. MDC is the correlation mechanism precisely because Spring Boot's structured logging (ADR OB04) picks up MDC natively; no per-call-site logging code was needed anywhere else in the codebase for "a single request traceable across Controller → ... → Response."

ADR OB04: Structured logging uses Spring Boot's native `logging.structured.format.console: ecs` — no new logging library, no custom encoder

Decision: `application.yml`'s base profile sets `logging.structured.format.console: ecs` (Elastic Common Schema JSON), active in all profiles including `dev` and `test` — no profile overrides it back to plain-text console output.
Rationale: Spring Boot 3.4+ (this project is on 3.5.0) ships structured logging support as a first-class `logging.structured.format.*` property — zero new dependency, zero custom `Encoder`/`Layout` class, directly satisfying "prefer industry-standard Spring Boot observability capabilities over custom implementations." MDC key-value pairs (including the correlation ID, ADR OB03) are automatically surfaced as structured fields with no per-log-site code. Applying it uniformly across all profiles — rather than reverting to plain text for local `dev` convenience — was a deliberate simplification: an untested profile-override (Spring profile documents layer as additional, higher-priority property sources rather than resetting unset keys, and no documented "off" sentinel value exists for this property) would have been unverified behavior shipped on faith; a developer running locally sees the same JSON shape production does, which is one less environment-specific behavior to reason about. The existing Security/Logging Policy table (`PROJECT_STATE.md`) — what data may appear in a log line at all — is unchanged by this milestone; structured logging changes the *shape* of log output, never *what* is logged.

ADR OB05: Custom `OllamaHealthIndicator`/`WeaviateHealthIndicator`, excluded from the `test` profile via `@Profile("!test")` — not a `management.health.*.enabled` toggle

Decision: Two new `HealthIndicator` beans (`infrastructure.observability`) each issue one short-timeout (2s) HTTP GET — Ollama's root URL, Weaviate's `/v1/.well-known/ready` (the identical endpoint `docker-compose.yml`'s own container healthcheck already uses) — returning `Health.up()`/`Health.down()`, never throwing. Both carry `@Profile("!test")`, so the beans simply don't exist when the `test` Spring profile is active. Both are included in the `readiness` health group (`management.health.group.readiness.include`), not `liveness`. Each class has a package-private constructor accepting a pre-built `RestTemplate` directly, alongside the public `@Autowired`-annotated, `RestTemplateBuilder`-based constructor Spring actually uses — enabling `MockRestServiceServer`-based unit tests without a live Ollama/Weaviate.
Rationale: An initial attempt used `management.health.ollama.enabled: false` / `management.health.weaviate.enabled: false` in the `test` profile; empirically (caught by this milestone's own `RagEndToEndIT` health-endpoint test, per the mandatory self-review) this did **not** prevent the beans from being instantiated and probed, and `/actuator/health` returned `503` in the test suite because both indicators genuinely failed to reach unreachable hosts. `@Profile("!test")` is unambiguous — the bean is never created in that profile — where the enabled-toggle's applicability to a hand-written (non-auto-configured) `HealthIndicator` turned out to be unreliable. Readiness, not liveness, because an unreachable Ollama or Weaviate means the app can't currently serve generation/retrieval requests, not that the app process itself is broken; tying liveness to an external dependency is a well-known Kubernetes anti-pattern that causes unnecessary container restarts when the *dependency*, not the app, is having a bad moment. The dual-constructor shape mirrors no existing precedent in this codebase but is the standard, minimal way to make a `RestTemplate`-based component testable with Spring's own `MockRestServiceServer` without reaching for a mocking HTTP server library.

---

# P05.5 ADRs — Operational Hardening & Phase 5 Completion

**Scope note:** This milestone stabilizes and hardens the platform built across P05.1–P05.4; it introduces no new platform capability. Every ADR below either closes a gap a prior milestone explicitly deferred (with a citation to that prior ADR/Deferred-Items entry) or documents a finding from this milestone's own repository audit. Where a fix would have required real architectural expansion — a separate Weaviate HTTP client configuration surface that doesn't exist in Spring AI 1.0.0 — it is documented as deferred, not implemented, per this milestone's own "document, don't expand" instruction.

ADR HD01: `UploadDocumentUseCase` is no longer `@Transactional` at the class level; each DB-touching step keeps its own short transaction, and pipeline failures now call the domain's own `Document.markFailed(String)`

Decision: The class-level `@Transactional` on `UploadDocumentUseCase` was removed outright (not narrowed, not replaced with `TransactionTemplate`). `documentService.registerDocument(...)`, `chunkApplicationService.saveAll(...)`, and `documentService.updateDocument(...)` are each calls to a **separate** Spring bean that already carries its own `@Transactional` boundary (`DocumentApplicationService`, `ChunkApplicationService`) — removing the outer annotation means each of those calls now gets its own independent, short-lived transaction/connection, while Tika parsing, file storage, the Ollama embedding call, and the Weaviate indexing call in between run holding no database connection at all. Steps 2–14 (everything after the initial registration) are wrapped in a single `try/catch(RuntimeException)`; on any failure, `document.markFailed(ex.getMessage())` is called and persisted via `documentService.updateDocument(document)` before the original exception is rethrown.
Rationale: This is the exact "long-running transaction spanning external I/O" concern P04.13.8 identified and explicitly deferred ("should be fixed before ingestion is load-bearing"), and the exact review item this milestone's brief names by class name. No new abstraction was introduced — the fix relies entirely on Spring's existing per-bean `@Transactional` proxying, which already existed on the two collaborator services; removing the outer annotation was sufficient, verified by confirming (not assuming) that both collaborators are independently `@Transactional`. Separately, `Document.markFailed(String)` has existed since the domain model was designed (present since before P05.1) but had never actually been called anywhere in the codebase — a document that failed mid-pipeline was previously left silently stuck in whatever PENDING/PARSING/CHUNKING/EMBEDDING status it last reached, indistinguishable from "still in progress." Wiring the existing domain capability up is graceful degradation with zero new domain surface.

ADR HD02: Tenant validation (ADR TN01) extended to `renameConversation` and `deleteConversation` — the two ownership-scoped `ConversationApplicationService` methods P05.3 explicitly deferred

Decision: `RenameConversationCommand` gained a `tenantId` field; `ConversationApplicationService.deleteConversation` and `DeleteConversationUseCase.execute` both gained a `tenantId` parameter. Both methods now call the existing `requireTenantMatch` helper after their ownership-scoped fetch, identically to `getConversation`/`addUserMessage`/`addAssistantMessage`. Neither method is reachable via REST today (no rename or delete route exists) — `renameConversation` has no caller anywhere in the codebase at all; `deleteConversation` is called only by `DeleteConversationUseCase`, itself unreached by REST.
Rationale: This is the literal, named "unreachable methods missing tenant validation" example from this milestone's brief, and the exact gap `PROJECT_STATE.md`'s Deferred Items table tracked as "Genuine technical debt (P05.3)... Close this the moment either method gets a route." Fixing it now — while it's still cheap and isolated, with zero live callers to coordinate around — is strictly better than waiting for a future milestone to both wire a new route *and* remember this pre-existing gap at the same time. `renameConversation` itself was reviewed for deletion (zero callers, arguably dead code) but kept: it is a small, correct, self-contained capability that will obviously need a REST route eventually (title editing is a normal conversation-management feature), and this codebase's own precedent (P04.13.8) already classifies build-ahead-of-consumer capabilities as "Future roadmap," not "delete it."

ADR HD03: Ollama HTTP calls get an explicit connect/read timeout via a `RestClientCustomizer`; Weaviate client timeouts are documented as deferred, not implemented — no configuration surface exists for them in Spring AI 1.0.0

Decision: New `infrastructure.config.HttpClientTimeoutConfig` registers a `RestClientCustomizer` bean (`app.ollama.connect-timeout-ms` / `app.ollama.read-timeout-ms`, defaulting to 5s/60s) that sets a bounded `ClientHttpRequestFactory` on the shared, Boot-managed `RestClient.Builder`. This works because Spring AI's `OllamaApiAutoConfiguration.ollamaApi(...)` takes an `ObjectProvider<RestClient.Builder>` — the exact same Boot-managed builder every `RestClientCustomizer` touches — rather than constructing its own HTTP client independently; **confirmed by decompiling the actual `spring-ai-autoconfigure-model-ollama-1.0.0.jar` bytecode**, not assumed from documentation. The equivalent fix for Weaviate was investigated the same way: `spring-ai-autoconfigure-vector-store-weaviate-1.0.0.jar`'s `WeaviateVectorStoreProperties` exposes only `scheme`/`host`/`apiKey`/`objectClass`/`consistencyLevel`/`headers`/`filterField` — no timeout property of any kind — and `WeaviateVectorStoreAutoConfiguration.weaviateClient(...)` constructs `io.weaviate.client.WeaviateClient` directly from those properties with no builder-customization hook comparable to `RestClientCustomizer`. Adding Weaviate timeouts would require overriding the auto-configured `WeaviateClient` bean entirely and hand-constructing `io.weaviate.client.Config` — genuine architectural expansion into a third-party client's construction, not a property tweak — so it is documented as deferred technical debt, not implemented.
Rationale: Without any timeout, a stalled Ollama response could block the calling thread indefinitely rather than failing into the existing retry/backoff `OllamaLlmAdapter` (ADR R02) and `EmbeddingService` already have — exactly the "timeout handling" gap this milestone's brief names. The read timeout (60s default) is deliberately generous, not aggressive: a legitimate large completion on CPU-only Ollama can genuinely take tens of seconds, and a timeout tuned to kill slow-but-working generations would trade one production incident for another. Verifying the propagation path against real bytecode (rather than trusting Spring AI's documentation, which doesn't clearly state this for the exact 1.0.0 release) was a deliberate choice after finding, in ADR OB05 during the previous milestone, that an assumption about a similar Spring Boot convention (`management.health.*.enabled`) turned out to be wrong — the same mistake was not worth repeating here on a change with less immediate test coverage (there is no live Ollama in the test suite to prove the timeout fires).

ADR HD04: `RetrievalService` now catches infrastructure-level `RuntimeException`s and rewraps them as `RetrievalException`; `InvalidRetrievalRequestException` gets its own `@ExceptionHandler` ahead of the generic upstream-failure one

Decision: `RetrievalService.doRetrieve()`'s body is now wrapped in `try { ... } catch (RetrievalException ex) { throw ex; } catch (RuntimeException ex) { throw new RetrievalException(...) }`. `HybridRetrievalException`, `QueryRewriteException`, and `VectorStoreException` (and its subtypes `VectorIndexingException`/`VectorSearchException`) all extend plain `RuntimeException` directly, not `RetrievalException` — before this change, any of them escaping `RetrievalService` would fall through `GlobalExceptionHandler`'s `{GenerationException, RetrievalException, LlmException}` → 502 mapping straight to the generic `Exception.class` → 500 handler, misreporting a genuine upstream failure as an unexplained server error. Separately, `GlobalExceptionHandler` gained `@ExceptionHandler(InvalidRetrievalRequestException.class)` → 400, registered ahead of the existing `RetrievalException` → 502 handler even though `InvalidRetrievalRequestException extends RetrievalException` (Spring dispatches to the most specific matching handler, so this is safe without reordering anything).
Rationale: This is "exception consistency," named explicitly in this milestone's brief, and both fixes were found by tracing the actual exception class hierarchy against what `GlobalExceptionHandler` maps — not by inspection alone. The `InvalidRetrievalRequestException` gap was live and REST-reachable today: `SendMessageRequest.content()` has no length constraint at the DTO/Bean-Validation level, so a message longer than `RetrievalService.MAX_QUERY_LENGTH` (10,000 characters) was, before this fix, returned to the caller as `502 Bad Gateway — "upstream failure"` — actively misleading, since the problem was entirely the caller's oversized input, not anything upstream. The infrastructure-exception-wrapping fix mirrors the pattern `OllamaLlmAdapter` already established for LLM failures (wrap at the boundary into a properly-hierarchy-placed exception) — applied here at the *application*-layer call site instead of inside the adapter, because `RetrievalException` lives in `application.retrieval`, not a shared domain package `HybridRetrievalException`/`QueryRewriteException`/`VectorStoreException` (all infrastructure-layer types) could extend without infrastructure reaching upward into the application layer for a type — a design smell even though no ArchUnit rule currently forbids it.

ADR HD05: `DB_PASSWORD` no longer has a fallback default in the base/production Spring profile; `dev`/`test` profiles each get an explicit, profile-scoped default instead — mirroring `JWT_SECRET_KEY`'s existing pattern exactly. A new, opt-in `management.server.port` lets operators isolate actuator endpoints onto a separate port without any code change

Decision: `spring.datasource.password: ${DB_PASSWORD:ka_pass}` became `${DB_PASSWORD}` (no default) in the base profile; `ka_pass` moved to the `dev` profile block, and the `test` profile block gained its own explicit `password: test` (Testcontainers ignores the value functionally, but Spring still requires the property to resolve to *something* since the base profile no longer supplies a fallback). Separately, `server.port: ${SERVER_PORT:8080}` and `management.server.port: ${MANAGEMENT_PORT:${server.port}}` were added — with no `MANAGEMENT_PORT` set, this is a complete no-op (management stays on the same port, identical to today's behavior); setting `MANAGEMENT_PORT` to a different value in a real deployment causes Spring Boot to serve actuator on a separate embedded connector with its own (permissive-by-default) security configuration, entirely decoupled from this application's custom JWT-based `SecurityFilterChain`.
Rationale: `application.yml` already treated `JWT_SECRET_KEY` this way (no base default, explicit profile-scoped dev/test values) — `DB_PASSWORD` is an equally real secret and had never been brought in line with that precedent; this was a genuine, if easy-to-overlook, "production-safe defaults" gap named explicitly in this milestone's Configuration Hardening scope. The `management.server.port` addition directly closes the item `PROJECT_STATE.md`'s Deferred Items table tracked from P05.4 (ADR OB01): `/actuator/metrics`/`/actuator/prometheus` currently require the same JWT every other protected endpoint does, which is safe but not how a real Prometheus deployment typically scrapes (anonymously, relying on network isolation instead). Building that isolation via a genuinely separate management port was explicitly named in ADR OB01 as "the standard production pattern... but stood up correctly it also changes deployment topology... explicitly out of [P05.4's] scope." This milestone is exactly where that deferral pointed. The fix is a pure, zero-risk-by-default configuration addition — no `docker-compose.yml` change was made, since that file is dev-only tooling, not a production deployment manifest; a real deployment sets `MANAGEMENT_PORT` and exposes it according to its own network topology.

ADR HD06: `spring-boot-starter-aop` and MapStruct (`mapstruct`, `mapstruct-processor`, `lombok-mapstruct-binding`) removed from `build.gradle` — confirmed zero usage across the entire codebase, not assumed

Decision: Both dependency groups were removed outright. Confirmed via a full-repository grep (main and test sources) for `@Aspect`/`org.aspectj`/`@Pointcut`/`@Around`/`@Before` (zero matches) and `@Mapper`/`mapstruct`/`MapStruct` (zero matches) before removal, then confirmed safe by a full `clean compileJava compileTestJava test` run afterward — build succeeds, all 606 tests pass.
Rationale: This milestone's brief explicitly asks for a dependency review ("unused dependencies... remove only where safe"). Every domain-entity mapper in this codebase (`UserPersistenceMapper`, `ChatSessionPersistenceMapper`, `RetrievedChunkMapper`, and others) is hand-written, `@Component`-annotated, not MapStruct-generated — a reasonable, valid choice for DDD aggregates with private constructors and factory methods, which don't fit MapStruct's typical getter/setter-based generation model well, but one that left the MapStruct annotation-processor toolchain declared and never invoked. `spring-boot-starter-aop` was similarly present with no `@Aspect` class anywhere to weave; Spring's own proxy-based `@Transactional` interception (used pervasively in this codebase, including the transaction-boundary fix in ADR HD01) comes from `spring-tx`/`spring-aop`, already pulled in transitively by `spring-boot-starter-data-jpa`, and needs no AspectJ weaving support. "Remove only where safe" was honored by verifying absence of usage first, then verifying the build and full test suite afterward — not by inference alone.
