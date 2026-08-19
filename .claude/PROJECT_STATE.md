# Project State

Current Version

v0.7.0 (Complete) — P06.1: Product Completeness & Authorization Depth — REST Surface Foundation (Phase 6, milestone 1 of N)

**Namespace:** Root package is `com.mudassirshahzad.eka` (renamed from `com.mudassir.eka` in R01 — pure namespace refactor, no behavioral or architectural change).

---

## Phase 4 — Foundation (Complete)

| Milestone | Description                        | New Tests | Status     |
|-----------|------------------------------------|-----------|------------|
| P04.1     | Retrieval Foundation               | —         | ✅ Complete |
| P04.2     | Vector Retrieval                   | —         | ✅ Complete |
| P04.3     | PostgreSQL BM25 Retrieval          | —         | ✅ Complete |
| P04.4     | Reciprocal Rank Fusion             | —         | ✅ Complete |
| P04.AC1   | Single Embedding Pipeline          | +10       | ✅ Complete |
| P04.5     | Hybrid Retrieval                   | +16       | ✅ Complete |
| P04.6     | Query Rewriting                    | +21       | ✅ Complete |
| P04.7     | Context Assembly                   | +28       | ✅ Complete |
| P04.8     | Prompt Builder                     | +50       | ✅ Complete |
| P04.9     | Chat Generation (LLM + Guardrails) | +82       | ✅ Complete |
| P04.10    | Conversation Memory                | +23       | ✅ Complete |
| P04.11    | Citation Engine                    | +20       | ✅ Complete |
| P04.12    | Enterprise Output Guardrails       | +14       | ✅ Complete |
| P04.13    | Architecture Reconciliation        | +19       | ✅ Complete |

**Phase 4 total tests: 489 — 0 failures**

---

## Phase 5 — Application Platform

| Milestone | Description                                     | New Tests | Status     |
|-----------|--------------------------------------------------|-----------|------------|
| P05.1     | End-to-End RAG Orchestration & REST Exposure     | +41       | ✅ Complete |
| P05.2     | Authentication Foundation                       | +21       | ✅ Complete |
| P05.3     | Tenant & Role Authorization Boundary            | +22       | ✅ Complete |
| P05.4     | Observability Foundation                        | +20       | ✅ Complete |
| P05.5     | Operational Hardening & Phase 5 Completion       | +13       | ✅ Complete |

**Grand total tests: 606 — 0 failures**

**Phase 5 is complete.**

---

## v0.6.1 — Engineering Excellence & Repository Governance (post-Phase-5)

Not a Phase 6 milestone — a read-only independent audit (conducted after v0.6.0) produced findings this milestone closes. No new business functionality or platform capability was introduced.

| Item | Description | Status |
|------|-------------|--------|
| 1 | CI/CD Foundation (GitHub Actions: build + test + ArchUnit on PR/push) | ✅ Complete |
| 2 | Repository Protection documentation (branch protection, not applied from code) | ✅ Complete |
| 3 | Gradle/release/app version alignment (`springBoot { buildInfo() }`) | ✅ Complete |
| 4 | Exception handling hardening (`GlobalExceptionHandler extends ResponseEntityExceptionHandler`) | ✅ Complete |
| 5 | JWT startup validation (fail-fast on weak secret / bad expiry) | ✅ Complete |
| 6 | Login rate limiting (`LoginRateLimiter`, in-memory, simple) | ✅ Complete |
| 7 | Request size protection (`RequestSizeLimitFilter`) | ✅ Complete |
| 8 | Dead Use Case layer resolved (Create wired + domain invariant; Get/List removed) | ✅ Complete |
| 9 | Document ingestion REST endpoint — intentionally deferred (ADR EX09) | ✅ Decided, documented |
| 10 | Production Dockerfile for the application | ✅ Complete (not empirically `docker build`-verified — no Docker daemon available) |

**Grand total tests: 623 — 0 failures** (net +17: +21 new, −4 removed as obsolete after ADR EX08)

**Phase 6 remains intentionally unscoped, pending explicit direction.**

---

## Milestone Governance (GitHub)

GitHub Milestones were formally introduced as a repository governance mechanism after v0.6.1 (ADR GOV01). This section is the authoritative record of the milestone model — keep it in sync whenever a milestone opens, closes, or work moves between milestones.

### Model

- **GitHub Releases** remain the historical record of shipped work — one release per tag, matching `CHANGELOG.md`. Releases are never retroactively edited.
- **GitHub Milestones** represent active or major delivery goals, not a parallel history — they track what's still ahead (with one intentional exception below), not a re-statement of what Releases already record.
- **Phase milestones** (e.g. `Phase 6`) represent implementation milestones — a body of engineering work, scoped the way the P0x.y milestones above already are.
- **Version milestones** (e.g. `Version 1.0.0`) represent product-release milestones — everything required before a specific version number can ship, which may span more than one phase milestone.

### Current milestone state

| Milestone | State | Scope |
|---|---|---|
| Enterprise Foundation Complete | Closed | Everything through v0.6.1 (Foundation → Phase 5 → Engineering Excellence & Repository Governance). The one intentionally-preserved retrospective milestone — see below. |
| Phase 6 | Open | Tracks all Phase 6 implementation work. Scope not yet defined. |
| Version 1.0.0 | Open | Tracks every remaining deliverable required before Project EKA is declared stable/production-ready. |

### Retrospective milestones — one exception, not a pattern

`Enterprise Foundation Complete` is deliberately the **only** retrospective milestone this repository will ever create. Historical phases (P04.x, P05.x) are represented by Git tags and GitHub Releases, not by creating a milestone for each one after the fact — Releases already are the historical record (see Model above); a per-phase milestone would just be a second copy of that same history. Do not create a "Phase 4 Complete" or "Phase 5 Complete" milestone retroactively — that history lives in `CHANGELOG.md` and the corresponding GitHub Releases.

### Release Workflow (canonical, ADR GOV02)

This is the standard sequence for every future milestone/release — the actual engineering process this repository follows, not an aspirational one:

1. Code complete
2. Self review
3. Architecture review (when applicable)
4. Documentation synchronization
5. Version alignment
6. Commit
7. Push
8. CI passing (mandatory)
9. Git tag creation
10. GitHub Release publication
11. GitHub Milestone review — close completed milestones, move unfinished work if necessary
12. Repository state verification

Steps run in this order because each depends on the one before it: there is no point tagging (9) before CI is green (8), no point creating a release (10) from an untagged commit, and no point reviewing milestones (11) before the release that closes them actually exists. "Repository state verification" (12) is the final step, not a formality — confirm the working tree is clean and everything pushed matches everything tagged/released before calling the milestone done.

### Repository Completion Checklist (permanent)

Every future milestone's completion must satisfy all of the following before it's considered done:

- ☐ Working tree clean
- ☐ Self review complete
- ☐ Architecture review complete (when applicable)
- ☐ Documentation synchronized
- ☐ ADRs updated (if required)
- ☐ `CHANGELOG.md` updated
- ☐ `PROJECT_STATE.md` updated
- ☐ `ROADMAP.md` updated
- ☐ Version numbers synchronized (`build.gradle`, tag, release, `CHANGELOG.md` all agree)
- ☐ Commit created
- ☐ Push successful
- ☐ CI passing
- ☐ Git tag created
- ☐ GitHub Release published
- ☐ GitHub Milestone reviewed
- ☐ Repository state verified

If a milestone is complete: close it. If work doesn't land in time: move the remaining scope to the next open milestone explicitly — don't leave a milestone open indefinitely with stale, half-finished scope.

### Lightweight milestone rules

- One milestone per major implementation phase.
- One milestone per major product version, when a version genuinely spans more than one phase.
- No retrospective milestones beyond the one exception above.
- Once this repository begins using GitHub Issues, Issues become the primary objects attached to milestones — until then, milestones function as governance and release checkpoints rather than issue containers (today: 0 issues attached to any milestone, by design, not oversight).
- Keep this proportional: no GitHub Projects board, no Issue Templates, no custom Labels until Issues actually become the primary work-tracking mechanism — introducing them earlier would be bureaucracy without anything real to track.

---

## Roadmap to v1.0.0 (Frozen)

**Status: FROZEN (v1.0 Roadmap Freeze, ADR GOV03).** This is the single, official roadmap from v0.6.1 to v1.0.0. `docs/roadmap.md` predates this model, is marked superseded for numbering/status, and is retained as historical content only (its Phase 6–9 material informed Phase 7/Phase 8 below, but does not define them). Do not reorder Phase 6/7/8 or reinterpret v1.0.0 scope without an explicit new planning session — this is deliberately not something to silently drift.

### Why this sequence (critical-review summary)

A second, adversarial pass against the drafted plan changed two things before freezing it — recorded here so the reasoning isn't lost:

- **Weaviate client timeout** (deferred technical debt since P05.5, ADR HD03) was previously unscheduled. It belongs in Phase 7: a hung Weaviate call can hang a retrieval request indefinitely, which is squarely an operational-integrity problem, not a someday-nice-to-have.
- **Prompt-injection / indirect-injection risk review** was missing from the draft entirely. Phase 6 opens real document ingestion to real users — the moment arbitrary user-supplied document content can be retrieved into an LLM prompt, indirect prompt injection becomes a live risk, not a theoretical one. Added to Phase 7 as a direct consequence of what Phase 6 ships.

Everything else held up under review: the 3-phase structure (make it reachable → make it good and safe → make it scale) is kept as-is — collapsing Phase 6 and 7 would mix new construction with quality gates in one phase, which this project's own review philosophy already rejects elsewhere; adding a 4th phase would split closely-related work for no benefit. MCP/LangGraph/Agentic AI remain explicitly post-v1.0 — the project's own Architectural Readiness Assessment (`docs/roadmap.md`) already rates them "High readiness," meaning deferring them costs nothing today.

### Official Project EKA v1.0.0 definition

| Dimension | Definition |
|---|---|
| Intended users | Mid-size to large organizations running an internal, self-hosted knowledge assistant over their own documents. Not a consumer product — every design decision (tenant isolation, on-prem model hosting, no external API dependency) targets an IT/security-conscious enterprise buyer. |
| Primary use cases | Upload internal documents; ask natural-language questions and receive cited, grounded answers; scope results by tenant, role, and document classification; maintain a persistent, auditable conversation history per user. |
| Functional capabilities | Full ingestion lifecycle reachable over REST (upload, status, list, delete). Fine-grained authorization enforced at the retrieval layer, not just the tenant boundary. Complete conversation management (create, list, rename, delete) exposed over REST. Admin capability to provision tenants and users without direct database access. At least one retrieval re-ranking stage beyond RRF fusion. |
| Security expectations | Authorization Filter live and tested. Refresh tokens with revocation — a leaked access token must be killable. Indirect prompt-injection risk reviewed against real ingested content. A documented secrets-management upgrade path beyond env vars (doesn't have to be built, has to be written down). |
| Operational expectations | Prometheus scraping and a Grafana dashboard for metrics that already exist. Postgres↔Weaviate reconciliation job running on a schedule with alerting on drift, not passive logging. Weaviate client timeout configured — no unbounded-hang path in the retrieval pipeline. |
| Deployment expectations | Docker image verified by a real `docker build` in CI (not just written and assumed). Single-instance deployment is the supported shape for v1.0.0; any component that would silently break under a second instance (today: `LoginRateLimiter`) is either made instance-safe or explicitly documented as a single-instance constraint — not silently left. |
| Production readiness expectations | Branch protection actually applied (`docs/governance/branch-protection.md` executed, not just written). Dependency/vulnerability scanning wired into CI. |
| Explicit out-of-scope items | MCP server, LangGraph orchestration, multi-agent platform, streaming (SSE) responses beyond what Phase 8 ships, semantic caching, external cloud LLM providers (Bedrock, Azure OpenAI), microservice extraction, Kafka event bus, S3/cloud storage migration. All rated "High readiness" in the project's own architectural assessment — the ports they'd plug into already exist, so deferring them costs nothing architecturally. |

### Phase 6 — Product Completeness & Authorization Depth

- **Objective:** Make every capability that already exists internally actually reachable, and correctly scoped, before building anything new.
- **Scope:** Document ingestion REST surface; Authorization Filter (the retrieval-pipeline stage `.claude/CLAUDE.md` has named as planned since before Phase 4); admin/registration REST surface; conversation list and delete routes (use cases already exist and are tested — only the routes are missing).
- **Success criteria:** A brand-new tenant can be fully operated — users provisioned, documents uploaded, conversations held and managed — without a single direct database write. Retrieval results are provably scoped by document-level authorization in a test, not just by tenant.
- **Exit criteria:** Full REST surface live for ingestion, conversations, and admin. Authorization Filter enforced and covered by tests proving a wrong-classification request is denied. Audit finding H2 (post-Phase-5 audit) formally closed, not merely documented as deferred.
- **Deliverables:** `POST /api/v1/documents` (+ status/list/delete routes); Authorization Filter as a retrieval-pipeline stage; admin/registration REST endpoints; `GET/DELETE` conversation routes wired to the already-existing `GetConversationUseCase`-equivalent/`DeleteConversationUseCase`.
- **Dependencies:** None blocking. Every port and application-layer service this phase needs already exists; this is a REST-exposure and one net-new authorization-enforcement phase, not a from-scratch build.

**Phase 6 milestone tracking:**

| Milestone | Version | Description | New Tests | Status |
|---|---|---|---|---|
| P06.1 | v0.7.0 | Product Completeness & Authorization Depth — REST Surface Foundation (document ingestion, admin/bootstrap, conversation list/delete REST surfaces) | +50 | ✅ Complete |
| P06.2 | v0.7.1 | Authorization Filter (retrieval-pipeline stage) | — | ⏳ Not started |
| P06.3 | v0.7.2 | Not yet scoped | — | ⏳ Not started |
| P06.4 | v0.7.3 | Not yet scoped | — | ⏳ Not started |
| P06.5 | v0.7.4 | Not yet scoped — Phase 6 Complete gate | — | ⏳ Not started |

**Grand total tests: 673 — 0 failures** (net +50)

**Phase 6 is not yet complete** — P06.1 deliberately excluded the Authorization Filter (explicit "out of scope" in its own brief); Phase 6's own exit criteria aren't met until that ships too. Do not tag `v0.8.0` (Phase 7) as a phase-complete boundary until P06.5 (the Phase 6 Complete gate) closes. Versioning within Phase 6 was refined to one point release per P06.x milestone rather than one version for the whole phase (ADR GOV04) — P06.3/P06.4/P06.5 have reserved version numbers only; their scope is deliberately not defined here and will be set in its own planning session before implementation, same as P06.2.

### Phase 7 — Retrieval Quality & Operational Integrity

- **Objective:** Raise answer quality and close the operational gaps that would turn into a real incident under production load. Assumes real traffic is now flowing through the REST surface Phase 6 built.
- **Scope:** Cross-encoder re-ranking + HyDE evaluation; Postgres↔Weaviate reconciliation job; refresh tokens with revocation; Weaviate client connect/read timeout (ADR HD03, closed here); indirect prompt-injection risk review against real ingested content; dependency/vulnerability scanning in CI; branch protection actually applied; Docker image build-verified in CI.
- **Success criteria:** Measurable relevance improvement on an internal evaluation set. A simulated partial-write failure is detected and repaired without manual intervention. A compromised access token can be revoked without waiting out its expiry. A hung Weaviate call can no longer hang a retrieval request indefinitely.
- **Exit criteria:** Quality benchmark result documented in an ADR. Reconciliation job running on a schedule with alerting. CI includes SCA. Branch protection live. Prompt-injection review documented with either mitigations shipped or residual risk explicitly accepted.
- **Deliverables:** Re-ranking adapter (cross-encoder); HyDE evaluation results; scheduled reconciliation job; refresh-token issuance/revocation; `RestClientCustomizer`-equivalent (or custom `WeaviateClient` bean) for Weaviate timeouts; prompt-injection review write-up; CI dependency scanning; applied branch protection; CI-verified Docker build.
- **Dependencies:** Phase 6's ingestion REST surface — re-ranking can't be meaningfully evaluated, and prompt-injection risk can't be meaningfully reviewed, against a corpus that only ever enters the system through test fixtures.

### Phase 8 — Scale & Ecosystem Readiness

- **Objective:** Prepare for more than one instance and for external consumption — without committing to an ecosystem before there's a real reason to.
- **Scope:** Prometheus + Grafana deployment; streaming (SSE) responses; distributed rate-limit store (conditional — only if a genuine multi-instance deployment is actually being planned by the time this phase starts); MCP server spike (go/no-go recommendation, not delivery).
- **Success criteria:** Dashboards exist and are actually used to answer an operational question. Streaming measurably reduces perceived response latency. The MCP spike produces a written go/no-go, not an assumption that it belongs in v1.0.0.
- **Exit criteria:** Metrics deployment live. Streaming shipped on at least the primary chat endpoint. MCP go/no-go documented in an ADR either way.
- **Deliverables:** Prometheus scrape endpoint + Grafana dashboard definitions; SSE streaming on `sendMessage`; (conditional) distributed rate-limit store; MCP spike write-up.
- **Dependencies:** Phase 7's operational-integrity work — dashboards are much less useful without the reconciliation job's signals feeding them.

### Release strategy

| Version | Milestone | Gate |
|---|---|---|
| v0.6.1 | Enterprise Foundation Complete | Shipped — closed milestone |
| v0.7.0 | P06.1 — REST Surface Foundation | Shipped — document ingestion, admin/bootstrap, conversation list/delete REST surfaces live |
| v0.7.1 | P06.2 — Authorization Filter | Retrieval-pipeline authorization enforced, tested against a wrong-classification denial |
| v0.7.2 | P06.3 | Not yet scoped |
| v0.7.3 | P06.4 | Not yet scoped |
| v0.7.4 | P06.5 — Phase 6 Complete | Every Phase 6 exit criterion (see above) met |
| v0.8.0 | Phase 7 complete | Re-ranking shipped, reconciliation job live, branch protection applied |
| v0.9.0 | Phase 8 complete | Metrics dashboarded, streaming shipped, MCP go/no-go decided |
| v1.0.0 | Version 1.0.0 milestone | Every item in the official v1.0.0 definition above is met — reviewed as a gate, not assumed from phase completion alone |

Follows the Release Workflow already codified above (ADR GOV02) — no new process, this is the existing model applied forward. Row split for v0.7.0–v0.7.4 reflects ADR GOV04's refinement of Phase 6 versioning to one point release per P06.x milestone; the Phase 7/8/v1.0.0 entry versions are unchanged from ADR GOV03.

---

## Architecture Notes

Architecture is frozen.

Security layer (Authorization Filter) is planned but not implemented.

### Frozen ADRs

| ADR  | Decision                                                                                          |
|------|---------------------------------------------------------------------------------------------------|
| G01  | `PromptBuilderPort` returns `PromptRequest` (provider-independent); LLM adapter converts         |
| G02  | `PromptRequest.userText` is the original verbatim query, never the rewritten retrieval form       |
| G03  | Source markers are 1-based: `[SOURCE:N]` where N = chunk.position() + 1                          |
| G04  | `TemplateBasedPromptBuilderAdapter` is stateless — same inputs always produce same output         |
| G05  | `PromptBuilderPort` signature frozen; future params travel inside `PromptBuildRequest`            |
| G06  | System prompt template at `classpath:prompts/qa-system.txt`; missing = fail fast at startup      |
| G07  | `ToolDefinition` is a stub passthrough for P04.8/P04.9; no execution occurs                      |
| G08  | `PromptBuildRequest` is the single frozen input to `PromptBuilderPort`                            |
| G09  | `LlmPort.generate()` takes `LlmRequest` (wraps `PromptRequest` + `GenerationOptions`)            |
| G10  | `LlmResponse` fields: generatedText, finishReason, modelName, promptTokens, completionTokens, latencyMs |
| G11  | `LlmPort` is synchronous; streaming deferred to future `StreamingLlmPort`                         |
| G12  | `LlmException` base in domain; infrastructure subtypes in `infrastructure.llm.exception`         |
| G13  | Retry logic belongs inside the provider adapter, not `GenerationService`                          |
| G14  | `GenerationOptions.DEFAULT` = (maxTokens=2048, temperature=0.1, topP=1.0, modelNameOverride=null) |
| G15  | Multi-model routing via future `RoutingLlmAdapter` pattern (mirrors `HybridRetrievalAdapter`)    |
| G16  | `OutputGuardrailsPort` receives text only; `CitationPort` receives text + `AssembledContext`     |
| M01  | `ConversationHistoryPort` is read-only; writing history is a separate concern                    |
| M02  | `conversationId` is optional on `GenerationRequest`; null = stateless generation                 |
| M03  | Memory window size (`app.conversation.memory-window-size`) is application config, not per-request |
| M04  | `InMemoryConversationHistoryAdapter` is the P04.10 seam; replaceable without domain/app changes  |
| C01  | `PositionalCitationAdapter` parses `[SOURCE:N]` via hand-written scan, not regex                 |
| C02  | Marker resolution keys off `AssembledChunk.position()`, not list index                           |
| C03  | Duplicate marker references dedupe by `chunkId`, keeping first-appearance order                  |
| C04  | Malformed/out-of-range markers are silently ignored; `CitationPort` never throws                 |
| C05  | `PositionalCitationAdapter` is the sole `CitationPort` implementation; passthrough seam removed  |
| GR01 | `OutputGuardrailsPort` stays text-only (G16); finish-reason-aware blocking deferred              |
| GR02 | Null/blank generated text resolves to `GuardrailResult.block(...)`; `apply()` never throws       |
| GR03 | Oversized responses are truncated and `PASS`ed, not blocked                                      |
| GR04 | Malformed-output normalisation strips non-printable control chars only (not `\r\n\t`)             |
| GR05 | `PolicyBasedOutputGuardrailsAdapter` is the sole `OutputGuardrailsPort` implementation            |
| R01  | `PersistentConversationHistoryAdapter` is the sole `ConversationHistoryPort` impl; reads via `ConversationRepository` |
| R02  | `OllamaLlmAdapter` retries transient failures (3 attempts, exponential backoff) — reconciles G13  |
| R03  | `roles.created_at` schema drift fixed via `V017` migration, not documented as debt                |
| R04  | `test` profile's `flyway.clean-on-validation-error` removed (property no longer exists)          |
| R05  | `--enable-preview` removed from Gradle build; zero preview features were ever used                |
| O01  | `RagOrchestrationService` is the sole cross-cutting turn coordinator; existing services unchanged |
| O02  | `addAssistantMessage` mirrors `addUserMessage` exactly — same ownership check, same event         |
| O03  | REST API versioned by `/api/v1` path prefix; errors are RFC 7807 `ProblemDetail` via one handler  |
| O04  | `RetrievalResult` carries `effectiveQueryText`; `RetrievalPort` interface itself unchanged         |
| O05  | Temporary permissive `SecurityFilterChain` (ADR-approved) — replaced outright in P05.2, not layered |
| A01  | JWT access tokens are signed/verified with HS256 (symmetric key); RS256 deferred                  |
| A02  | Login issues an access token only; no refresh/logout endpoint yet                                 |
| A03  | `AuthenticateUserUseCase` (application) verifies identity; only `JwtTokenProvider` (api) mints a JWT |
| A04  | `JwtAuthenticationFilter` never rejects a request; only `RestAuthenticationEntryPoint` returns 401 |
| A05  | `tenantId`/`userId` removed from request DTOs; both now come from the validated JWT               |
| A06  | `AuthenticateUserUseCase` always hashes-and-compares (dummy hash for unknown users) to avoid a login timing side channel |
| AZ01 | `AuthorizationInterceptor` (`HandlerInterceptor`) is the sole role-decision point; never re-validates ownership |
| AZ02 | Role policy: `VIEWER`/`AUDITOR` read-only; `USER`/`ADMIN` may create conversations and send messages |
| AZ03 | `AccessDeniedException` → 403 handled by `GlobalExceptionHandler`, not a security-layer `AccessDeniedHandler`; `ADMIN` does not bypass ownership |
| TN01 | Tenant isolation is a defensive post-fetch check in `ConversationApplicationService` (extends ADR R01's pattern) |
| OW01 | Resource ownership stays at `findByIdAndUserId` (pre-existing); mismatch resolves to 404, never 403 |
| OB01 | Actuator: `/actuator/health` + `/actuator/info` public; `/actuator/metrics`/`/actuator/prometheus` stay JWT-gated |
| OB02 | Latency via Micrometer `Observation` (retrieval/generation/orchestration); failure counters only where Boot doesn't already provide the signal; never tag metrics by raw tenant/user/conversation ID |
| OB03 | `CorrelationIdFilter` registered before Spring Security's own first filter; MDC-propagated; echoed via response header only |
| OB04 | Structured logging via Spring Boot native `logging.structured.format.console: ecs`; no new logging library |
| OB05 | `OllamaHealthIndicator`/`WeaviateHealthIndicator` excluded from `test` profile via `@Profile("!test")`, not an enabled-toggle; in the `readiness` group, not `liveness` |
| HD01 | `UploadDocumentUseCase` is no longer class-level `@Transactional`; each collaborator service keeps its own short transaction; failures call the pre-existing `Document.markFailed(String)` |
| HD02 | `renameConversation`/`deleteConversation` extended with the ADR TN01 tenant check, closing the gap ADR TN01/OW01 explicitly deferred |
| HD03 | Ollama HTTP calls get a `RestClientCustomizer`-based connect/read timeout; Weaviate client timeouts have no configuration surface in Spring AI 1.0.0 and are documented as deferred, not implemented |
| HD04 | `RetrievalService` rewraps infrastructure `RuntimeException`s as `RetrievalException`; `InvalidRetrievalRequestException` gets its own 400 handler ahead of the 502 `RetrievalException` handler |
| HD05 | `DB_PASSWORD` has no base-profile default (mirrors `JWT_SECRET_KEY`, ADR A01-adjacent pattern); dev/test profiles set it explicitly; opt-in `management.server.port` allows isolating actuator endpoints without code changes |
| HD06 | `spring-boot-starter-aop` and MapStruct dependencies removed after confirming zero usage repo-wide |
| EX01 | GitHub Actions CI (`gradle clean build` on every PR/push to `main`); branch protection documented, not applied from code |
| EX02 | `GlobalExceptionHandler extends ResponseEntityExceptionHandler`; framework client errors get correct 4xx, not generic 500 |
| EX03 | Gradle `version` is the sole source of truth for the release number; `/actuator/info` surfaces `info.build.version` via `buildInfo()`, not a hand-maintained string |
| EX04 | `JwtProperties` compact constructor validates HS256 key strength + expiry positivity at startup, not on first use |
| EX05 | `LoginRateLimiter` — in-memory, per-IP, fixed-window (10/min default) counter guarding `/api/v1/auth/login`, checked before credential verification |
| EX06 | `RequestSizeLimitFilter` rejects oversized request bodies (`Content-Length` check) before Spring MVC/Jackson ever see them |
| EX07 | `SecurityConfig`'s stale "authorization out of scope" Javadoc corrected — false since P05.3 shipped `AuthorizationInterceptor` |
| EX08 | `CreateConversationUseCase` wired to the controller + title invariant moved into `Conversation` domain aggregate; `GetConversationUseCase`/`ListConversationsUseCase` deleted (added no value) |
| EX09 | Document ingestion REST endpoint intentionally deferred — new platform capability, out of this milestone's scope |
| EX10 | Production `Dockerfile` for the Spring Boot app added; `docker-compose.yml` untouched (no deployment redesign) |
| GOV01 | GitHub Milestones introduced as repository governance; Releases stay the sole historical record; one retrospective milestone only (Enterprise Foundation) |
| GOV02 | Canonical, numbered 12-step Release Workflow + 15-item Repository Completion Checklist |
| GOV03 | Roadmap to v1.0.0 frozen (Phase 6 → 7 → 8 → v1.0.0); `docs/roadmap.md` superseded for numbering/status, retained for content |
| GOV04 | Phase 6 versioning refined to one point release per P06.x milestone (v0.7.0–v0.7.4, Phase 6 Complete at v0.7.4); Phase 7/8/v1.0.0 entry versions unchanged |
| PC01 | `RequestSizeLimitFilter`'s multipart exemption is scoped to the exact upload route (method + path), not any multipart-content-typed request — closes a self-review-caught bypass |
| PC02 | `UserApplicationService.getUser`/`activateUser`/`deactivateUser` now verify tenant ownership after fetch — closed a real cross-tenant gap this milestone made reachable for the first time |
| PC03 | No `Tenant` domain aggregate/port introduced; bootstrap operates against an already-provisioned tenant; a nonexistent tenant surfaces via a new `DataIntegrityViolationException` → 400 handler |
| PC04 | `GlobalExceptionHandler` gained `ApplicationException` → 400 and `DataIntegrityViolationException` → 400 handlers |
| PC05 | Documents stay tenant-wide readable (not owner-scoped); admin surface deliberately minimal (bootstrap/register/get/deactivate only); `DeleteConversationUseCase` given its first route |

---

## RAG Orchestration Pipeline (P05.1 — current, outermost)

```
HTTP POST /api/v1/conversations/{id}/messages
       │
       ▼
ConversationController.sendMessage()         ← api.controller (thin: validate, convert, invoke, convert)
       │ @RequireRole({USER, ADMIN}) enforced by AuthorizationInterceptor before this method runs (P05.3, ADR AZ01/AZ02)
       │ SendMessageCommand
       ▼
RagOrchestrationService.handleUserMessage()  ← application.orchestration (P05.1, ADR O01)
       │
       ├─▶ ConversationApplicationService.addUserMessage()        — ownership + tenant check, persist user turn (ADR TN01/OW01)
       ├─▶ RetrievalService.retrieve()                             — ranked chunks + effectiveQueryText (ADR O04)
       ├─▶ ContextAssemblyPort.assemble()                          — AssembledContext
       ├─▶ GenerationService.generate()                            — see "Generation Pipeline" below
       └─▶ ConversationApplicationService.addAssistantMessage()   — ownership + tenant check, persist assistant turn (ADR O02/TN01)
       │
       ▼
GeneratedAnswerResponse (DTO — never a domain model, ADR O03)
```

Errors from any step surface as an RFC 7807 `ProblemDetail` via `api.exception.GlobalExceptionHandler` (ADR O03). As of P05.2, every request to this endpoint requires a valid JWT — `api.config.SecurityConfig`'s temporary permissive seam (ADR O05) has been replaced outright, not layered on top of. As of P05.3, a valid JWT is not sufficient by itself — see "Authorization Pipeline" below.

---

## Authentication Pipeline (P05.2 — new)

```
HTTP POST /api/v1/auth/login {tenantId, email, password}
       │
       ▼
AuthController.login()                        ← api.controller
       │
       ├─▶ AuthenticateUserUseCase.execute()   ← application.user — verifies email/password/tenantId/active
       │       (throws InvalidCredentialsException on any failure — same exception for every cause)
       │
       └─▶ JwtTokenProvider.generateAccessToken()  ← api.security — mints an HS256 access token (ADR A01)
       │
       ▼
LoginResponse {accessToken, tokenType: "Bearer", expiresInMs}


HTTP <any other request>  Authorization: Bearer <token>
       │
       ▼
JwtAuthenticationFilter                        ← api.security (ADR A04: never rejects, only populates)
       │ valid token → SecurityContext gets a JwtAuthenticationToken(userId, tenantId, authorities)
       │ missing/invalid token → SecurityContext stays empty, request continues
       ▼
Spring Security AuthorizationFilter             ← permitAll for login/health/docs, authenticated() otherwise
       │ unauthenticated + protected endpoint → RestAuthenticationEntryPoint → 401 ProblemDetail
       ▼
ConversationController                          ← reads tenantId/userId from JwtAuthenticationToken (ADR A05),
                                                    never from the request body
```

---

## Authorization Pipeline (P05.3 — new)

```
HTTP <any /api/v1/** request except /api/v1/auth/**>
       │  SecurityContext already holds a JwtAuthenticationToken (Authentication Pipeline above)
       ▼
AuthorizationInterceptor.preHandle()      ← api.security, registered by api.config.WebMvcConfig (ADR AZ01)
       │  no @RequireRole on the target method → permitted, unconditionally
       │  @RequireRole present → principal's authorities must intersect the required roles
       │  role insufficient → AccessDeniedException
       ▼
ConversationController.<method>()          ← invoked only if the interceptor returned true
       │
       ▼
ConversationApplicationService.<method>()  ← findByIdAndUserId (ownership, pre-existing) +
                                              requireTenantMatch (tenant, new — ADR TN01)
       │  not found OR wrong tenant OR wrong owner → ResourceNotFoundException (404, never 403 — ADR OW01)
       ▼
<normal response>
```

`AccessDeniedException` (from the interceptor) and `ResourceNotFoundException` (from the ownership/tenant check) both surface via the existing `api.exception.GlobalExceptionHandler` (ADR O03/AZ03) — 403 and 404 respectively. No role, including `ADMIN`, bypasses the ownership/tenant check (ADR AZ03). Fine-grained, metadata-based content filtering integrated into the retrieval pipeline itself (`ROADMAP.md`'s "Full Authorization Filter") remains unbuilt — this pipeline is the REST-boundary half only.

---

## Observability (P05.4 — new)

```
HTTP <any request>
       │
       ▼
CorrelationIdFilter                 ← api.observability — first filter in the whole chain (ADR OB03),
       │                              ahead of Spring Security itself, not just JwtAuthenticationFilter
       │  generates or reuses X-Correlation-Id → MDC["correlationId"] for the request's duration,
       │  echoed on the response header; cleared in `finally` regardless of outcome
       ▼
[ normal Authentication → Authorization → Orchestration → Retrieval → Generation → Persistence chain ]
       │  every log line emitted anywhere in that chain automatically carries the correlation ID
       │  (Spring Boot's structured ECS console format — ADR OB04 — surfaces MDC natively)
       ▼
<response>  (X-Correlation-Id header always present, success or error)
```

Latency: `RetrievalService.retrieve()` / `GenerationService.generate()` / `RagOrchestrationService.handleUserMessage()` each run inside an `eka.retrieval` / `eka.generation` / `eka.orchestration` Micrometer `Observation` (ADR OB02) — a timer today, trace-ready with no further code changes once distributed tracing (out of scope) is added. Request count/latency/error-rate is Spring Boot's auto-instrumented `http.server.requests` — not duplicated by a custom metric.

Failure counters: `eka.auth.failures{type=token|credentials}` (`JwtAuthenticationFilter`, `AuthenticateUserUseCase`); `eka.authz.failures{reason=role|ownership}` (`AuthorizationInterceptor`, `ConversationApplicationService`). No metric anywhere is tagged by a raw tenant/user/conversation ID (unbounded-cardinality risk — ADR OB02).

Health: `/actuator/health` aggregates the default DB indicator plus new `OllamaHealthIndicator`/`WeaviateHealthIndicator` (`infrastructure.observability`, ADR OB05) — both excluded via `@Profile("!test")` in tests, both in the `readiness` group (not `liveness`). `/actuator/health/liveness` and `/actuator/health/readiness` are enabled via `management.endpoint.health.probes.enabled`.

Actuator exposure: `/actuator/health` and `/actuator/info` are `permitAll` (ADR OB01); `/actuator/metrics` and `/actuator/prometheus` require the same JWT every other protected endpoint does.

---

## Generation Pipeline (P04.10)

```
GenerationRequest (with optional conversationId)
       │
       ▼
ConversationHistoryPort.getRecentMessages()  ← PersistentConversationHistoryAdapter (P04.13: reads via ConversationRepository)
       │ List<Message> (empty when conversationId is null, unknown, or belongs to another tenant)
       ▼
PromptBuilderPort.build()           ← TemplateBasedPromptBuilderAdapter
       │ PromptRequest (memoryMessages now populated)
       ▼
LlmPort.generate()                  ← OllamaLlmAdapter (inserts memory msgs between system + user)
       │ LlmResponse
       ▼
OutputGuardrailsPort.apply()        ← PolicyBasedOutputGuardrailsAdapter (P04.12: blocks null/blank, strips control chars, truncates oversized text)
       │ GuardrailResult
       ▼
CitationPort.resolve()              ← PositionalCitationAdapter (P04.11: parses [SOURCE:N], resolves against AssembledContext)
       │ List<Citation>
       ▼
GeneratedResponse
```

`tools` remain an empty passthrough (populated at agent milestone).

---

## Generation Pipeline (P04.9)

```
GenerationRequest
       │
       ▼
PromptBuilderPort.build()           ← TemplateBasedPromptBuilderAdapter
       │ PromptRequest
       ▼
LlmPort.generate()                  ← OllamaLlmAdapter
       │ LlmResponse
       ▼
OutputGuardrailsPort.apply()        ← PassthroughOutputGuardrailsAdapter (seam → P04.11)
       │ GuardrailResult
       ▼
CitationPort.resolve()              ← PassthroughCitationAdapter (seam → P04.11)
       │ List<Citation>
       ▼
GeneratedResponse
```

`memoryMessages` and `tools` are empty `List.of()` stubs; populated in P04.10 and agent milestone respectively, without changing port signatures.

---

## Infrastructure Wiring

- `HybridRetrievalAdapter` is `@Primary`; `WeaviateRetrievalAdapter` is `@Qualifier("vectorRetrieval")`; `PostgresBm25RetrievalAdapter` is `@Qualifier("bm25Retrieval")`
- `OllamaLlmAdapter` is the sole `LlmPort` implementation
- `PersistentConversationHistoryAdapter` is the sole `ConversationHistoryPort` implementation (reads via `ConversationRepository`, tenant-checked — P04.13)
- `PolicyBasedOutputGuardrailsAdapter` implements `OutputGuardrailsPort` (blocks null/blank output, strips control characters, truncates to `app.guardrails.max-response-length` — P04.12)
- `PositionalCitationAdapter` implements `CitationPort` (parses `[SOURCE:N]` markers, resolves against `AssembledContext` by `AssembledChunk.position()` — P04.11)
- `RagOrchestrationService` is the sole caller wiring `ConversationApplicationService` + `RetrievalService` + `ContextAssemblyPort` + `GenerationService` together (P05.1, ADR O01)
- `ConversationController` is the sole REST entry point for conversations (`/api/v1/conversations`, now including list/delete — P06.1); `AuthController` is the sole token-issuing entry point (`/api/v1/auth/login`, P05.2); `DocumentController` is the sole REST entry point for documents (`/api/v1/documents`, P06.1); `AdminController` is the sole REST entry point for bootstrap/user administration (`/api/v1/admin`, P06.1); `GlobalExceptionHandler` is the sole `@RestControllerAdvice` (P05.1, ADR O03)
- `SecurityConfig` requires a valid JWT on every endpoint except `/api/v1/auth/login`, `/actuator/health`, `/actuator/info` (P05.4, ADR OB01), and the Swagger/OpenAPI paths — real HS256 validation as of P05.2 (ADR A01), replacing the P05.1 permissive seam outright (ADR O05)
- `JwtTokenProvider` (`api.security`) is the sole component that signs or verifies tokens; `JwtAuthenticationFilter` is the sole component that populates the `SecurityContext`; `RestAuthenticationEntryPoint` is the sole source of a 401 response (ADR A04)
- `AuthenticateUserUseCase` (`application.user`) is the sole verifier of login credentials, via the existing `UserRepository` port and a `BCryptPasswordEncoder` (`infrastructure.config.PasswordEncoderConfig`) — always throws the same `InvalidCredentialsException` regardless of failure cause (ADR A03)
- `springdoc-openapi-starter-webmvc-ui` auto-exposes `/v3/api-docs` and `/swagger-ui.html` from the same controller/DTO annotations — no separate spec to keep in sync (P05.1)
- `AuthorizationInterceptor` (`api.security`), registered by `WebMvcConfig` (`api.config`) against every `/api/v1/**` route, is the sole role-authorization decision point — reads `@RequireRole` off the target controller method (P05.3, ADR AZ01)
- `ConversationApplicationService.getConversation`/`.addUserMessage`/`.addAssistantMessage` are the sole tenant/ownership decision points for conversations — a private `requireTenantMatch` helper, extending the ADR R01 pattern, runs after every ownership-scoped fetch (P05.3, ADR TN01/OW01)
- `GlobalExceptionHandler` gained `AccessDeniedException` → 403 (P05.3, ADR AZ03) alongside its existing mappings
- `CorrelationIdFilter` (`api.observability`) is the sole component that generates/propagates the correlation ID and populates MDC; registered first in `SecurityConfig`'s filter chain, ahead of Spring Security itself (P05.4, ADR OB03)
- `OllamaHealthIndicator`/`WeaviateHealthIndicator` (`infrastructure.observability`) are the sole custom `/actuator/health` contributors beyond Spring Boot's default DB indicator; both `@Profile("!test")`, both in the `readiness` health group (P05.4, ADR OB05)
- `RetrievalService`/`GenerationService`/`RagOrchestrationService` each wrap their existing body in a Micrometer `Observation` (`eka.retrieval`/`eka.generation`/`eka.orchestration`) — a timer today, trace-ready with no further changes (P05.4, ADR OB02)
- `JwtAuthenticationFilter`/`AuthenticateUserUseCase` increment `eka.auth.failures`; `AuthorizationInterceptor`/`ConversationApplicationService` increment `eka.authz.failures` — both `MeterRegistry`-based, no metric ever tagged by a raw tenant/user/conversation ID (P05.4, ADR OB02)
- `UploadDocumentUseCase` no longer carries a class-level `@Transactional`; `DocumentApplicationService`/`ChunkApplicationService` each keep their own, giving the pipeline short per-step transactions instead of one long one spanning Tika/Ollama/Weaviate calls; pipeline failures now call `Document.markFailed(...)` (P05.5, ADR HD01)
- `ConversationApplicationService.renameConversation`/`.deleteConversation` now call `requireTenantMatch` identically to the three previously-checked methods — every ownership-scoped method in this service is now tenant-checked (P05.5, ADR HD02)
- `HttpClientTimeoutConfig` (`infrastructure.config`) registers a `RestClientCustomizer` bounding Ollama's connect/read timeouts (`app.ollama.connect-timeout-ms`/`read-timeout-ms`); no equivalent exists for Weaviate — deferred, not a code gap (P05.5, ADR HD03)
- `RetrievalService.doRetrieve()` rewraps any infrastructure `RuntimeException` (e.g. `HybridRetrievalException`, `QueryRewriteException`, `VectorStoreException`) as `RetrievalException`, so `GlobalExceptionHandler`'s 502 mapping actually reaches them; `GlobalExceptionHandler` gained a more-specific `InvalidRetrievalRequestException` → 400 handler ahead of the 502 handler (P05.5, ADR HD04)
- `spring.datasource.password` has no base-profile default (mirrors `JWT_SECRET_KEY`); `management.server.port` is an opt-in escape hatch for isolating actuator endpoints in a real deployment (P05.5, ADR HD05)
- `GlobalExceptionHandler extends ResponseEntityExceptionHandler` — Spring MVC's own framework exceptions (malformed JSON, non-UUID path variables, unsupported methods) now resolve to correct 4xx `ProblemDetail` responses instead of the generic 500 fallback (v0.6.1, ADR EX02)
- `JwtProperties`'s compact constructor validates HS256 key strength (≥32 bytes) and positive expiry at application-context startup, not on first token signed (v0.6.1, ADR EX04)
- `LoginRateLimiter` (`api.security`) gates `AuthController.login()` — in-memory, per-IP, fixed-window (10/min default), checked before credential verification; `ProjectEkaApplication` gained `@EnableScheduling` for its periodic cleanup sweep (v0.6.1, ADR EX05)
- `RequestSizeLimitFilter` (`api.security`), registered in `SecurityConfig` right after `CorrelationIdFilter`, rejects any request whose `Content-Length` exceeds `app.request.max-body-bytes` (default 1 MiB) before Spring MVC/Jackson ever process it (v0.6.1, ADR EX06); exempts only `POST /api/v1/documents` multipart requests, matched on method + path + content type together, not content type alone (P06.1, ADR PC01)
- `ConversationController.createConversation` now calls `CreateConversationUseCase`, not `ConversationApplicationService` directly; the title invariant it used to duplicate now lives in `Conversation.create`/`.rename` (domain); `GetConversationUseCase`/`ListConversationsUseCase` were deleted (v0.6.1, ADR EX08)
- `build.gradle`'s `version` (now `0.6.1`, previously a permanent `1.0.0-SNAPSHOT` placeholder) is the sole source of truth for the release number; `springBoot { buildInfo() }` surfaces it at `/actuator/info` as `info.build.version` (v0.6.1, ADR EX03)
- `.github/workflows/build.yml` runs `gradle clean build` (full test suite + ArchUnit) on every PR and push to `main` — the repository's first CI gate (v0.6.1, ADR EX01)
- `DocumentController` (`/api/v1/documents`) reuses `UploadDocumentUseCase`/`GetDocumentUseCase`/`ListDocumentsUseCase`/`DeleteDocumentUseCase` unchanged; reads/lists stay tenant-wide, not owner-scoped, matching `DocumentApplicationService`'s pre-existing semantics (P06.1, ADR PC05)
- `AdminController` (`/api/v1/admin`) exposes exactly four endpoints — public `POST /bootstrap` (first-user-only, guarded by `UserApplicationService.tenantHasAnyUser`), `POST /users`, `GET /users/{id}`, `POST /users/{id}/deactivate`, all `ADMIN`-only except bootstrap (P06.1, ADR PC03/PC05)
- `UserApplicationService.getUser`/`.activateUser`/`.deactivateUser` now call a `requireTenantMatch` helper identical in shape to `ConversationApplicationService`'s (P06.1, ADR PC02); `UserRepository` gained `existsByTenantId`
- `GlobalExceptionHandler` gained `ApplicationException` → 400 and `DataIntegrityViolationException` → 400 handlers (P06.1, ADR PC04)
- `PageResponse<T>` (`api.dto`) is the shared paginated-list response shape for both `DocumentController.listDocuments` and `ConversationController.listConversations` (P06.1)

---

## Package Structure (production code)

```
com.mudassirshahzad.eka
├── domain
│   ├── chunk                        — ChunkId, Chunk
│   ├── conversation                 — Conversation (title invariant enforced in create/rename,
│   │                                  MAX_TITLE_LENGTH=500 — v0.6.1, ADR EX08), Message, MessageRole,
│   │                                  Citation
│   ├── document                     — DocumentId, Document
│   ├── generation
│   │   ├── exception                — LlmException
│   │   ├── model                    — FinishReason, GenerationOptions, LlmRequest, LlmResponse,
│   │   │                              GuardrailStatus, GuardrailResult, GeneratedResponse,
│   │   │                              PromptBuildRequest, PromptRequest, ToolDefinition
│   │   └── port                     — LlmPort, OutputGuardrailsPort, CitationPort, PromptBuilderPort
│   ├── retrieval
│   │   ├── model                    — RetrievalOptions, RetrievedChunk, RetrievalResult,
│   │   │                              SearchMetadata, AssembledChunk, AssembledContext
│   │   └── port                     — RetrievalPort, RankingPort, QueryRewritePort, ContextAssemblyPort
│   └── shared                       — TenantId
├── application
│   ├── generation                   — GenerationRequest, GenerationException, GenerationService
│   ├── orchestration                — SendMessageCommand, RagTurnResult, RagOrchestrationService (P05.1)
│   ├── retrieval                    — RetrievalRequest, RetrievalException,
│   │                                  InvalidRetrievalRequestException, RetrievalService
│   │                                  (wraps infra exceptions — P05.5, ADR HD04)
│   ├── conversation                 — RenameConversationCommand now carries TenantId (P05.5, ADR HD02);
│   │                                  CreateConversationUseCase now the controller's actual entry
│   │                                  point (v0.6.1, ADR EX08); GetConversationUseCase/
│   │                                  ListConversationsUseCase removed (added no value, ADR EX08)
│   └── user                         — RegisterUserUseCase, GetUserUseCase (tenantId param — P06.1,
│                                      ADR PC02), DeactivateUserUseCase, AuthenticateUserUseCase
│                                      (P05.2, ADR A03), UserApplicationService (requireTenantMatch
│                                      + tenantHasAnyUser — P06.1, ADR PC02/PC03)
├── infrastructure
│   ├── citation                     — PositionalCitationAdapter
│   ├── context                      — DefaultContextAssemblyAdapter
│   ├── config                       — DatabaseConfig, AsyncConfig, AppProperties,
│   │                                  PasswordEncoderConfig (P05.2),
│   │                                  HttpClientTimeoutConfig (P05.5, ADR HD03)
│   ├── conversation                 — PersistentConversationHistoryAdapter
│   ├── guardrails                   — PolicyBasedOutputGuardrailsAdapter
│   ├── llm
│   │   ├── exception                — LlmTimeoutException, LlmRateLimitException,
│   │   │                              LlmProviderUnavailableException, LlmInvalidResponseException,
│   │   │                              LlmModelNotFoundException
│   │   └── ollama                   — OllamaLlmAdapter
│   ├── observability                — OllamaHealthIndicator, WeaviateHealthIndicator (P05.4, ADR OB05)
│   ├── prompt                       — TemplateBasedPromptBuilderAdapter
│   ├── query.rewrite                — OllamaQueryRewriteAdapter, QueryRewriteException
│   ├── ranking                      — RrfRankingAdapter
│   └── retrieval
│       ├── hybrid                   — HybridRetrievalAdapter, HybridRetrievalException
│       ├── postgres                 — PostgresBm25RetrievalAdapter, Bm25MetadataFilterTranslator,
│       │                              Bm25ScoreNormalizer
│       └── weaviate                 — WeaviateRetrievalAdapter, WeaviateVectorStoreAdapter
└── api                              — first REST surface (P05.1)
    ├── config                       — SecurityConfig (real JWT validation, P05.2 — ADR A01/O05;
    │                                  correlation ID filter registration, P05.4 — ADR OB03),
    │                                  OpenApiConfig, WebMvcConfig (P05.3 — registers AuthorizationInterceptor)
    ├── controller                   — ConversationController (createConversation/sendMessage/
    │                                  deleteConversation @RequireRole-annotated — P05.3 ADR AZ02,
    │                                  P06.1; createConversation calls CreateConversationUseCase
    │                                  — v0.6.1 ADR EX08; listConversations/getConversation open to
    │                                  all roles), AuthController (P05.2; login rate-limited —
    │                                  v0.6.1 ADR EX05), DocumentController (P06.1, ADR PC05),
    │                                  AdminController (P06.1, ADR PC03/PC05)
    ├── dto                          — CreateConversationRequest, SendMessageRequest (both
    │                                  identity-free — ADR A05), ConversationResponse,
    │                                  ConversationDetailResponse, MessageResponse, CitationResponse,
    │                                  GeneratedAnswerResponse, LoginRequest, LoginResponse (P05.2),
    │                                  PageResponse<T>, DocumentResponse, BootstrapRequest,
    │                                  RegisterUserRequest, UserResponse (P06.1)
    ├── observability                — CorrelationIdFilter (P05.4, ADR OB03)
    ├── security                     — JwtProperties (startup-validates key strength/expiry — v0.6.1,
    │                                  ADR EX04), JwtTokenProvider, JwtAuthenticationToken,
    │                                  JwtAuthenticationFilter, RestAuthenticationEntryPoint (P05.2),
    │                                  RequireRole, AuthorizationInterceptor (P05.3, ADR AZ01),
    │                                  LoginRateLimiter, TooManyLoginAttemptsException (v0.6.1, ADR EX05),
    │                                  RequestSizeLimitFilter (v0.6.1, ADR EX06)
    └── exception                    — GlobalExceptionHandler (ADR O03; AccessDeniedException → 403, ADR AZ03;
                                       InvalidRetrievalRequestException → 400, P05.5 ADR HD04; extends
                                       ResponseEntityExceptionHandler — v0.6.1, ADR EX02;
                                       TooManyLoginAttemptsException → 429, v0.6.1, ADR EX05)
```

*(P04.13 correction: `ranking` and `context` were shown incorrectly/missing above — they are top-level `infrastructure` packages, not nested under `infrastructure.retrieval`.)*

### Repository Scope (P04.13.3)

This file's Milestone/ADR tracking above covers the **retrieval/generation pipeline** (P04.x). It does not cover a second, larger body of code — pre-existing Phase 1/2 foundation work (`docs/roadmap.md`) that predates the P04.x milestone-tracking discipline. Full detail on that layer is not duplicated here; this section exists solely so its existence and status are unambiguous.

| Package (domain / application / infrastructure) | Contents | Status |
|---|---|---|
| `domain.document`, `domain.chunk` | `Document`, `Chunk` aggregates | Used by both threads |
| `domain.user`, `domain.query` | `User`, `KnowledgeQuery` aggregates | Foundation-only |
| `application.document` | `ChunkingService`, `EmbeddingService`, `DocumentIndexingService`, ingestion use cases | **Now reachable via REST (P06.1)** — `DocumentController` calls `UploadDocumentUseCase`/`GetDocumentUseCase`/`ListDocumentsUseCase`/`DeleteDocumentUseCase` directly |
| `application.conversation` | `ConversationApplicationService` + CRUD use cases | Write side of P04.13's `ConversationHistoryPort` fix (ADR R01); **fully reachable via REST as of P06.1** — `ConversationController` now covers create/get/list/delete/send-message, the last two added this milestone |
| `application.chat`, `application.query` | Chat session and knowledge-query use cases | Foundation-only, not yet wired to `application.generation`/`application.retrieval` (see P04.13.8) |
| `application.user` | User registration/lookup/role/password use cases, plus `AuthenticateUserUseCase` (P05.2) | **Further reachable via REST as of P06.1** — `AdminController` calls `RegisterUserUseCase`/`GetUserUseCase`/`DeactivateUserUseCase`. Role management and password change remain unreached (ADR PC05, deliberate) |
| `application.event` + `infrastructure.event` | 17 domain event records + `SpringDomainEventPublisher` | Built, unused — no `@EventListener` anywhere (see P04.13.8) |
| `infrastructure.parsing`, `.embedding`, `.storage`, `.vectorstore` | Tika parsing, Ollama embedding, local file storage, Weaviate vector store (ingestion side) | Foundation-only ingestion adapters |
| `infrastructure.persistence.postgres` | 8 repository adapters, 12 entities, 7 mappers, 11 JPA repositories | Foundation-only; now has baseline tests (P04.13.4) |
| `infrastructure.config` | `DatabaseConfig`, `AsyncConfig`, `AppProperties`, `PasswordEncoderConfig` (P05.2) | Spring wiring for the foundation layer |

**Update (P05.1):** `com.mudassirshahzad.eka.api.controller.ConversationController` is now the first REST entry point (`/api/v1/conversations`), reaching `application.conversation` and `application.orchestration` directly. `application.chat`, `application.query`, and `application.document` remain unreached — no endpoint calls them yet, consistent with the P04.13.8 classification below (chat/query integration is P05-future, not this milestone).

**Update (P05.2):** `com.mudassirshahzad.eka.api.controller.AuthController` (`/api/v1/auth/login`) is a second REST entry point, reaching `application.user` for the first time — but only its new `AuthenticateUserUseCase`. There is still no registration/admin endpoint, so users must be seeded directly (e.g. via a migration or a one-off script) until a future milestone adds one; that gap is a known, accepted limitation of "Authentication Foundation," not an oversight.

**Update (P05.3):** `ConversationApplicationService.getConversation`/`.addUserMessage`/`.addAssistantMessage` (the three REST-reachable methods) now take and verify `TenantId` (ADR TN01). `.renameConversation` and `.deleteConversation` — still unreached by any endpoint (no rename/delete route exists) — were deliberately **not** given the same check; see Deferred Items below.

**Update (P05.5):** `.renameConversation` and `.deleteConversation` now take and verify `TenantId` as well (ADR HD02) — every ownership-scoped method on `ConversationApplicationService` is tenant-checked, closing the P05.3 gap noted above. Both remain unreached by any REST endpoint; the fix was made ahead of a route existing, not in response to one.

**Update (P06.1):** Three new REST entry points reach the foundation layer for the first time: `DocumentController` (`/api/v1/documents`) reaches `application.document`; `AdminController` (`/api/v1/admin`) reaches `application.user`'s registration/lookup/deactivation slice; `ConversationController` gained `GET /` and `DELETE /{id}`, reaching `.listConversations` and `DeleteConversationUseCase` for the first time. `UserApplicationService.getUser`/`activateUser`/`deactivateUser` gained the same defensive tenant check `ConversationApplicationService` got in P05.3/P05.5 (ADR PC02) — closed specifically because this milestone is what made them reachable. `application.chat`/`application.query` remain the only foundation-layer packages with zero REST reachability.

### Deferred Items (P04.13.8)

Reviewed without implementing — each classified so none of these become a future undocumented surprise:

| Item | Classification | Notes |
|---|---|---|
| Domain event system (17 events, zero `@EventListener` consumers) | Future roadmap | Not broken — built ahead of its consumers. Revisit when an analytics/audit/notification feature needs it. |
| `application.chat` (ChatSession) not wired to `application.generation` | Future roadmap (P04.14 — End-to-End RAG) | `RecordTurnCommand` is designed to receive exactly what `LlmResponse` already produces; natural fit for the next milestone, not this one. |
| `application.query` (KnowledgeQuery) not wired to `application.retrieval` | Future roadmap (P04.14 — End-to-End RAG) | Same reasoning — audit/tracking layer built ahead of the entry point that would call it. |
| `UploadDocumentUseCase`'s `@Transactional` spanning Tika/Ollama/Weaviate calls | **CLOSED (P05.5, ADR HD01)** | Class-level `@Transactional` removed; each collaborator service keeps its own short transaction. Failures now call `Document.markFailed(...)`, previously unused. |
| `AppProperties` (`@ConfigurationProperties`) vs. scattered `@Value` config binding | No action required | Both patterns are valid Spring idioms already in active use; forcing one convention across every adapter is cosmetic churn without measurable long-term value (rejected per review philosophy). |
| `ConversationApplicationService.renameConversation`/`.deleteConversation` lack the P05.3 tenant check (ADR TN01) | **CLOSED (P05.5, ADR HD02)** | Both methods now call `requireTenantMatch`, identically to the three previously-checked methods. Still unreached by any REST endpoint — closed ahead of exposure, not in response to it. |
| No registration/admin endpoint for `application.user` | **Partially CLOSED (P06.1)** | `POST /api/v1/admin/bootstrap` (first-user, public) and `POST /api/v1/admin/users` (ADMIN-only) now exist. Still no self-service registration, no list-users, no role-assignment/password-change REST — deliberately minimal (ADR PC05), not oversight. |
| `/actuator/metrics`/`/actuator/prometheus` require a JWT rather than being scraped anonymously | Partially addressed (P05.5, ADR HD05) | `management.server.port` now exists as an opt-in escape hatch — setting it moves actuator onto a separate embedded connector outside this app's JWT-based `SecurityFilterChain`, the standard production pattern. Not enabled by default (deployment-topology decision, left to the operator); `/actuator/metrics`/`/actuator/prometheus` remain JWT-gated when `MANAGEMENT_PORT` is unset. |
| Weaviate HTTP client has no configurable connect/read timeout | Deferred technical debt (P05.5, ADR HD03) | Verified via bytecode inspection of `spring-ai-autoconfigure-vector-store-weaviate-1.0.0.jar`: `WeaviateVectorStoreProperties` exposes no timeout property, and the auto-configured `WeaviateClient` bean has no `RestClientCustomizer`-equivalent hook. A fix would require overriding the auto-configured client and hand-constructing `io.weaviate.client.Config` — genuine architectural expansion, out of this milestone's "document, don't expand" scope. Revisit as a Phase 6 candidate. |
| No REST endpoint for document ingestion (`POST /api/v1/documents` does not exist) | **CLOSED (P06.1)** | `DocumentController` now exposes upload (multipart), get, list, and delete, all reusing `UploadDocumentUseCase`/`GetDocumentUseCase`/`ListDocumentsUseCase`/`DeleteDocumentUseCase` unchanged. Closes audit finding H2. |
| `DeleteConversationUseCase` has no REST route | **CLOSED (P06.1)** | `DELETE /api/v1/conversations/{id}` now calls it — its active-chat-session guard is exercised by a real caller for the first time. |
| Docker image (new `Dockerfile`, v0.6.1 ADR EX10) not empirically build-verified | Known gap, disclosed | No Docker daemon was available in the implementing environment; the Dockerfile was verified by careful reading against known-correct multi-stage Spring Boot patterns, not by an actual `docker build`. Whoever next has Docker available should confirm it builds and starts before relying on it in a real deployment. |
| No dependency vulnerability scanning (SCA) in CI | Not addressed this milestone | `.github/workflows/build.yml` (v0.6.1, ADR EX01) runs build/test/ArchUnit only; adding Dependabot/OWASP Dependency-Check was not in this milestone's numbered scope. Reasonable next CI addition. |
| No rate limiting beyond login; no distributed rate-limit store | Accepted limitation (v0.6.1, ADR EX05) | `LoginRateLimiter` is deliberately per-instance/in-memory — correct for the current single-instance deployment (`docker-compose.yml` defines no load balancer or replica count). Revisit with a shared store only if a multi-instance deployment shape is actually adopted. |
| No `Tenant` domain aggregate or repository port; tenant creation stays an ops/database concern | Deferred, intentionally (P06.1, ADR PC03) | `POST /api/v1/admin/bootstrap` operates against an already-provisioned, still-empty tenant — it does not create one. Building tenant provisioning would be new domain modeling, out of a "REST Surface Foundation" milestone's scope. A nonexistent tenant on bootstrap surfaces as a generic 400 (`DataIntegrityViolationException` handler), not a clean domain 404 — a known, accepted trade-off of not introducing a `TenantRepository` port for this alone. |
| Authorization Filter still not built | Open, explicitly out of P06.1's scope | Named in `.claude/CLAUDE.md`'s target architecture since before Phase 4; frozen as Phase 6's second milestone (P06.2) in the "Roadmap to v1.0.0" section above. Document reads/lists remain tenant-wide, not per-document-scoped, until this ships. |
| No list-users, role-assignment, or password-change REST endpoints | Deliberately minimal (P06.1, ADR PC05) | `UserApplicationService.activateUser`/`assignRole`/`removeRole`/`changePassword` all already work; no REST surface was added for them since nothing in this milestone's scope needed them. Add when a concrete caller/requirement exists, not speculatively. |

---

## Build Tool

Gradle 8.12 — no `gradlew` wrapper present.

Binary: `~/.gradle/wrapper/dists/gradle-8.12-bin/cetblhg4pflnnks72fxwobvgv/gradle-8.12/bin/gradle`

Java 21. `--enable-preview` removed in P04.13 (ADR R05) — no preview language feature was ever used.

CI (`.github/workflows/build.yml`, v0.6.1 ADR EX01) provisions the same Gradle 8.12 via `gradle/actions/setup-gradle` rather than a committed wrapper, matching this local-dev convention rather than diverging from it.

---

## Security / Logging Policy

Architecture constraint — enforced across all milestones:

| Data           | Allowed |
|----------------|---------|
| Tenant ID      | DEBUG / WARN / ERROR |
| Model name     | DEBUG |
| Finish reason  | DEBUG |
| Token counts   | DEBUG |
| Latency ms     | DEBUG |
| System text    | NEVER |
| User query     | NEVER |
| Chunk content  | NEVER |
| Generated text | NEVER |
| Prompt content | NEVER |
| Correlation ID | Any level (not sensitive — an opaque, per-request identifier) |
| Password / JWT / secrets / API keys | NEVER (P05.4 explicit requirement; already unwritten in this codebase) |

As of P05.4, console log output is structured JSON (ECS format, ADR OB04) in every profile — this table governs *what* may appear in a field, unchanged by the switch from plain-text to structured output. MDC (including the correlation ID) is surfaced automatically as structured fields; nothing above was loosened or tightened by that change.
