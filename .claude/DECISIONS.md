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
