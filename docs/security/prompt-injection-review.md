# Indirect Prompt-Injection Risk Review

**Status:** Complete — mitigations shipped, residual risk explicitly accepted
**Milestone:** Phase 7 / WP-5 (v0.8.4) · ADRs PI01–PI04
**Scope of review:** Every path by which content the platform did not author can reach a model prompt.

---

## 1. Why this review exists

Phase 6 opened document ingestion to real users over REST. From that moment, text written by
somebody other than the operator can be retrieved into a model prompt — which turns indirect prompt
injection from a theoretical concern into a live one. The frozen Phase 7 scope added this review for
exactly that reason, and it was written *after* the phase's retrieval work so it assesses the
pipeline as Phase 7 actually leaves it, not an earlier shape of it.

**Indirect** injection is the relevant variant. A direct attack is a user typing "ignore your
instructions" into the chat box, and it risks only that user's own session. An indirect attack is a
sentence buried in an uploaded PDF that the retriever later pulls into *someone else's* answer. The
attacker is not present when the payload fires, and the victim never sees the document.

## 2. Where untrusted content enters a prompt

Traced through the code rather than assumed. Two paths, one of which is new as of v0.8.3.

| # | Path | Sink | Introduced |
|---|---|---|---|
| 1 | Upload → Tika parse → chunk → index → retrieve → `ContextAssemblyPort` → `TemplateBasedPromptBuilderAdapter` | **System prompt**, via the `{context}` placeholder | v0.5.0 |
| 2 | Retrieve → `RetrievalService` → `LlmRerankAdapter` | **Scoring prompt**, as the passage being rated | **v0.8.3 (WP-4)** |

A third candidate was checked and ruled out: `HydeQueryRewriteAdapter` sends only the user's own
query to the model and never document content, so it does not widen this surface.

Path 2 deserves emphasis because WP-4 created it. It is also the *more* attractive target of the
two: manipulating one answer affects one reply, whereas manipulating the re-ranker promotes a
document into the context of **every subsequent query** that retrieves it.

## 3. What already limited the damage before this review

These were not built as injection defences, but they bound its blast radius and are worth stating
because they explain why the residual risk below is accepted rather than escalated:

- **Authorization Filter (P06.2, ADR AF03/AF04)** — classification clearance is enforced *before*
  content reaches any prompt. A document cannot be injected into the context of a caller not cleared
  to read it, so injection cannot cross a classification boundary.
- **Tenant isolation** — retrieval is unconditionally tenant-filtered, so a document uploaded by one
  tenant cannot reach another tenant's prompt at all. **This is the single most important limit
  here:** it makes the realistic attacker an insider within the same tenant, not an anonymous
  outsider.
- **Citations resolve positionally (ADR C02/C04)** — a model that invents a `[SOURCE:N]` marker
  produces no citation rather than a fabricated one, so injection cannot manufacture a false
  attribution to a document that was not retrieved.
- **Output guardrails (P04.12)** — control characters stripped, length capped, blank output blocked.
- **Logging policy** — prompt and chunk content are never logged, so a payload cannot pivot into log
  injection.

## 4. Mitigations shipped in this work package

### 4.1 Untrusted context is fenced and labelled as data (ADR PI01)

`prompts/qa-system.txt` now states before the context that the block is untrusted data, never a
source of instructions, and names the specific manipulations to disregard (role change, prompt
disclosure, answering a different question). The context sits between explicit fence markers.

### 4.2 A document cannot break out of its own fence (ADR PI01)

Fencing is worthless if a document can emit the closing marker itself — everything after it would
land in the region the template describes as trusted. `TemplateBasedPromptBuilderAdapter` therefore
strips both fence markers from chunk content before rendering. Markers are *removed*, not escaped:
they carry no legitimate meaning inside a document, so nothing is lost, and no escape sequence is
left for a second-order attempt to undo.

### 4.3 Instructions are restated after the context (ADR PI01)

The rules are re-asserted after the closing fence, so the last thing the model reads before the
user's question is the instruction hierarchy rather than whatever a document happened to end with.

### 4.4 The re-ranking prompt gets the same treatment (ADR PI02)

`LlmRerankAdapter` fences the passage, strips forged fence markers, and instructs the model that
text attempting to direct it is itself evidence the passage is not a genuine answer. The adapter's
pre-existing `[0,1]` score clamp also acts as a bound here: a fully manipulated model cannot push a
passage *above* a legitimately perfect one, only up to it.

### 4.5 What was deliberately **not** done

- **No content sanitisation or rewriting.** Retrieved text is the evidence an answer cites; silently
  editing it would corrupt citations and could change the meaning of a quoted policy. Only the fence
  markers are removed.
- **No injection-phrase detection or blocklist.** Pattern-matching for "ignore previous
  instructions" is trivially evaded by rewording and produces false positives on legitimate
  documents — a security policy document discussing prompt injection would be flagged by its own
  subject matter. It would add the appearance of a defence without the substance.
- **No second "is this injection?" model call.** That doubles cost and latency on every query and
  merely moves the same trust problem to a second model with the same weakness.

## 5. Residual risk — explicitly accepted

**The mitigations above are defence in depth, not a guarantee. A sufficiently well-crafted document
can still influence a model's behaviour.** No prompt-level technique closes this, because the
underlying cause is that instructions and data share one channel in a language model.

Accepted residual risk, stated plainly:

1. **A same-tenant insider who can upload a document can attempt to influence answers given to
   colleagues.** Tenant isolation and classification clearance bound *who* can be targeted; they do
   not prevent it within those bounds.
2. **Fencing and instruction hierarchy are probabilistic.** They raise the effort required and
   reduce success rates. They are not enforcement.
3. **Re-ranking manipulation can bias which documents are seen**, bounded by the score clamp and by
   the fact that re-ranking is off by default.
4. **This review is structural, not empirical.** `PromptInjectionResistanceTest` proves the prompts
   are *built* with these properties — fenced, non-escapable, instructions restated. Whether a given
   model *obeys* them was not measured, because doing so requires a live model and an adversarial
   corpus, neither of which exists here (the same constraint that left ADR RQ07 open).

**Why accepted rather than escalated:** the realistic attacker is an authenticated user inside a
single tenant, who by definition already has read access to that tenant's knowledge base and a
legitimate channel to distribute text to colleagues. The marginal capability injection grants such a
user is real but modest, and the alternatives — content rewriting, blocklists, a second model — each
cost accuracy, latency, or both, while providing no guarantee either.

## 6. Recommendations for after v1.0

Not Phase 7 scope; recorded so they are not rediscovered from scratch:

- **Activate the audit log** (dormant since V008, ADR HD09). Retrieval-to-answer provenance is the
  one control that makes a successful injection *investigable* after the fact. This is the highest-
  value follow-up on the list.
- **Empirical red-teaming** against a live model with an adversarial corpus, once a model is
  deployed — converting §5's structural review into a measured one.
- **Upload-time review for high-clearance documents**, if a tenant's threat model warrants it.
- **Per-document trust levels**, distinguishing operator-curated documents from user-uploaded ones,
  should that distinction ever become meaningful for a consumer of this platform.

## 7. Verification

- `PromptInjectionResistanceTest` — 8 tests asserting the structural properties above, including
  both fence-forgery attempts and the template placeholder not being re-expanded.
- Existing suites unchanged and green: classification enforcement, tenant isolation, citation
  resolution, output guardrails.
