# Roadmap

Completed

✅ P04.1 Retrieval Foundation

✅ P04.2 Vector Retrieval

✅ P04.3 PostgreSQL BM25 Retrieval

✅ P04.4 Reciprocal Rank Fusion

✅ P04.AC1 Single Embedding Pipeline

✅ P04.5 Hybrid Retrieval

✅ P04.6 Query Rewriting

✅ P04.7 Context Assembly

✅ P04.8 Prompt Builder

✅ P04.9 Chat Generation

✅ P04.10 Conversation Memory

✅ P04.11 Citation Engine

✅ P04.12 Enterprise Output Guardrails

✅ P04.13 Architecture Reconciliation

✅ P05.1 End-to-End RAG Orchestration & REST Exposure

✅ P05.2 Authentication Foundation

✅ P05.3 Tenant & Role Authorization Boundary

✅ P05.4 Observability Foundation

✅ P05.5 Operational Hardening & Phase 5 Completion

**Phase 5 (Application Platform) is complete.**

✅ v0.6.1 Engineering Excellence & Repository Governance (post-Phase-5, not Phase 6 — closes findings from the independent post-Phase-5 audit: CI/CD, branch protection docs, version alignment, exception handling, JWT startup validation, login rate limiting, request size limits, dead Use Case layer resolution, document-ingestion-endpoint deferral decision, application Dockerfile)

**Roadmap to v1.0.0 is frozen (v1.0 Roadmap Freeze, ADR GOV03).** Full detail — official v1.0.0 definition, per-phase objective/scope/success/exit criteria/deliverables/dependencies, release strategy — lives in `.claude/PROJECT_STATE.md`'s "Roadmap to v1.0.0 (Frozen)" section. This file stays a short index; do not duplicate that detail here.

✅ v0.7.0 — P06.1: Product Completeness & Authorization Depth — REST Surface Foundation (document ingestion REST surface, admin bootstrap/registration, conversation list/delete). First milestone of Phase 6.

✅ v0.7.1 — P06.2: Authorization Filter — role-based document-classification clearance (`PUBLIC`/`INTERNAL`/`CONFIDENTIAL`/`RESTRICTED`) enforced both in the retrieval pipeline (`RetrievalService`, post-fetch, engine-agnostic — ADR AF03) and across every REST document endpoint (`getDocument`/`listDocuments`/`deleteDocument` — ADR AF05), via a single shared `ClassificationPolicyPort` (ADR AF04). Fail-closed on unknown/null classification (ADR AF07); classification now mandatory at ingestion (ADR AF06). Closes the item named in `.claude/CLAUDE.md`'s target architecture since before Phase 4. Second milestone of Phase 6 — **Phase 6's exit criteria are now all met by P06.1+P06.2; Phase 6 itself is not yet formally closed** (see below).

Phase 6 versioning (ADR GOV04): one point release per P06.x milestone, not one version for the whole phase — P06.1 → v0.7.0 (shipped), P06.2 → v0.7.1 (shipped), P06.3 → v0.7.2, P06.4 → v0.7.3, P06.5 → v0.7.4 (Phase 6 Complete gate). Phase 7 still begins at v0.8.0, Phase 8 still begins at v0.9.0 — unchanged.

Pending (Phase 6 completion review)

○ Post-P06.2 self-review/architecture-review (the same discipline that produced v0.6.1 and P05.5) — decides whether P06.3 opens at all. Explicit decision made before P06.2 began: P06.3–P06.5 are **not** pre-scoped; a milestone opens only for a genuine gap the review finds, not speculatively. If none is found, Phase 6 completes directly at v0.7.1 and the reserved v0.7.2–v0.7.4 slots are recorded as deliberately unused.

Upcoming (frozen sequence, not yet started)

○ Phase 7 — Retrieval Quality & Operational Integrity. Re-ranking, HyDE, Postgres↔Weaviate reconciliation, refresh tokens, Weaviate client timeout, prompt-injection review, SCA in CI, applied branch protection. GitHub Milestone: create when Phase 6 closes, not before.

○ Phase 8 — Scale & Ecosystem Readiness. Metrics dashboards, streaming, conditional distributed rate limiting, MCP spike (go/no-go only). GitHub Milestone: create when Phase 7 closes, not before.

○ Version 1.0.0 — gate review against the frozen product definition in `.claude/PROJECT_STATE.md`. GitHub Milestone "Version 1.0.0" (open).

Post-v1.0 (explicitly out of scope before v1.0.0)

MCP server (full delivery, beyond Phase 8's spike)

LangGraph orchestration

Multi-agent platform

Semantic caching

External cloud LLM providers (Bedrock, Azure OpenAI)

Microservice extraction, Kafka event bus, S3/cloud storage migration

`docs/roadmap.md` is the historical, pre-milestone-governance version of this document — superseded for phase numbering and status, retained for technical content (Advanced Retrieval / MCP / LangGraph / Agentic AI Platform sections). It does not define current scope; this file and `.claude/PROJECT_STATE.md` do.
