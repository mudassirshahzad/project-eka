# Project EKA: Building a Production-Grade Enterprise Knowledge Platform

## The Problem

Enterprise knowledge is scattered. Specifications, compliance documents, and institutional expertise live in PDF libraries, SharePoint folders, and email threads — invisible to the people who need them. Standard search returns documents; engineers need answers. The gap between "we have this information" and "we can use this information" costs thousands of hours annually in repeated knowledge discovery.

The challenge is not finding an LLM — it is building a platform that retrieves the right context reliably, keeps data on-premises, maintains audit trails, and evolves without being rewritten.

---

## A Naive RAG Architecture — And Its Limits

The simplest RAG implementation is straightforward: embed documents, store vectors, retrieve similar chunks, pass them to an LLM, return a response. This works in a demo and fails in production.

The limitations surface quickly: single-tenant data models that cannot be retrofitted for isolation; services tightly coupled to a specific LLM SDK; stateless retrieval that produces incoherent follow-up answers; pure vector search that misses exact-match terminology where keyword search is more precise; no audit trail for regulated industries.

These are not edge cases. They are the standard failure modes of enterprise RAG.

---

## The Proposed Architecture

Project EKA is a **Modular Monolith** built on **Hexagonal Architecture** (Ports & Adapters). The domain — core business logic — carries zero framework dependencies. Every external system, including the LLM, vector database, and relational store, is accessed through a port interface. Adapters implement those interfaces in the infrastructure layer.

The consequence is significant: swapping Ollama for AWS Bedrock, or Weaviate for pgvector, requires changing one adapter. Domain logic, use cases, and APIs are unchanged.

---

## Why Weaviate

Weaviate solves two problems simultaneously. Its **native multi-tenancy** provides physical data isolation per tenant — actual shard separation, not application-level filtering. This is the correct model where tenant data leakage is a compliance risk, not just a bug.

Its **built-in hybrid search** blends semantic vector search with BM25 keyword ranking in a single query, weighted by a single parameter. This eliminates the need for a separate Elasticsearch cluster and produces better retrieval precision than either approach alone — particularly for domain-specific terminology where embedding models have partial understanding but keyword search is exact.

---

## Why a Modular Monolith

Microservices are an organisational solution to team ownership and independent deployment requirements — not a quality signal. Applied prematurely, they introduce distributed systems complexity and operational overhead that slows every early iteration.

Project EKA enforces strict internal module boundaries at build time, so bounded contexts can be extracted to independent services when demand genuinely justifies it. Extraction becomes a packaging decision, not a rewrite — the architectural work is never thrown away.

---

## Future MCP Integration

The Model Context Protocol transforms Project EKA from a user-facing application into an AI-accessible tool. Because use case interfaces already define the complete API contract, adding an MCP server is just another adapter — translating JSON-RPC calls to existing use case calls, with no domain changes. Any MCP-compatible client, including Claude Desktop and external orchestration agents, can then query the knowledge base as a tool rather than an application.

---

## Future LangGraph Integration

Every service in Project EKA is stateless. State lives in domain aggregates and their persistence. This is the prerequisite for graph-based orchestration: the graph holds state, each node calls a stateless service. When LangGraph is added, existing services become graph nodes without internal changes. This unlocks iterative retrieval loops, parallel search branches, relevance grading before generation, and self-correction when initial retrieval is insufficient.

---

## Key Lessons Learned

**Architectural decisions on day one are the most expensive to reverse.** Tenant isolation, provider abstraction, and hexagonal boundaries must be designed in — they cannot be retrofitted without significant cost.

**Retrieval quality is the product.** A strong retrieval pipeline with a modest model consistently outperforms a state-of-the-art model fed irrelevant context.

**Auditability is infrastructure, not a feature.** Compliance, debugging, and continuous improvement all depend on a complete, tamper-proof record of system behaviour.

**Provider independence is a business requirement.** LLM providers change pricing, deprecate models, and introduce compliance constraints. An architecture locked to a single provider is carrying unacceptable vendor risk.
