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
