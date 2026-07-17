# Project State

Current Version

v0.5.0 (In Progress)

Completed

- P04.1 Retrieval Foundation
- P04.2 Vector Retrieval
- P04.3 PostgreSQL BM25 Retrieval
- P04.4 Reciprocal Rank Fusion
- P04.AC1 Single Embedding Pipeline
- P04.5 Hybrid Retrieval
- P04.6 Query Rewriting
- P04.7 Context Assembly
- P04.8 Prompt Builder

Current Test Count

331 Passing

Current Milestone

P04.8 Complete

Next Milestone

P04.9 Chat Generation (LlmPort + OllamaLlmAdapter)

Notes

Architecture is frozen.

Security layer (Authorization Filter) is planned but not implemented.

ContextAssemblyPort returns AssembledContext (not String) — citation metadata is preserved.
DefaultContextAssemblyAdapter is pure Java with no LLM dependency.
Token estimation uses 4-chars-per-token heuristic.

PromptBuilderPort signature is frozen: PromptRequest build(PromptBuildRequest request).
PromptBuildRequest is the single input model — port signature never changes (ADR G08).
PromptRequest is provider-independent — no Spring AI types (ADR G01).
originalQueryText is always the user's verbatim question, never the rewritten form (ADR G02).
SOURCE markers are 1-based: [SOURCE:N] where N = chunk.position() + 1 (ADR G03).
Prompt template loaded from classpath:prompts/qa-system.txt at startup; missing template = fast fail (ADR G06).
memoryMessages and tools are List.of() today; populated in P04.10 and agent milestone without port-signature change.
TemplateBasedPromptBuilderAdapter is stateless — same inputs always produce same output (ADR G04).
