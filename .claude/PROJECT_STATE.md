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

Current Test Count

281 Passing

Current Milestone

P04.7 Complete

Next Milestone

P04.8 Prompt Builder

Notes

Architecture is frozen.

Security layer (Authorization Filter) is planned but not implemented.

ContextAssemblyPort now returns AssembledContext (not String) — citation metadata is preserved.
DefaultContextAssemblyAdapter is pure Java with no LLM dependency.
Token estimation uses 4-chars-per-token heuristic; precise tokenization planned for P04.8.
