# Contributing to Project EKA

Thank you for your interest in contributing. Project EKA is an open architecture exploration of enterprise AI, and every contribution — code, documentation, bug reports, and architectural discussion — is valued.

Please read this guide fully before opening a pull request. It keeps the review process smooth for everyone.

---

## Table of Contents

- [Getting Started](#getting-started)
- [Branch Strategy](#branch-strategy)
- [Commit Message Conventions](#commit-message-conventions)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Architecture Principles](#architecture-principles)
- [Documentation Requirements](#documentation-requirements)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Features](#suggesting-features)

---

## Getting Started

1. **Fork** the repository on GitHub
2. **Clone** your fork locally
3. **Install prerequisites:** Java 21, Docker, Gradle (or use the wrapper)
4. **Start infrastructure:**
   ```bash
   docker compose up -d postgres weaviate ollama
   ```
5. **Run the tests** to verify your setup:
   ```bash
   ./gradlew test
   ```
6. **Create a branch** following the conventions below and start working

---

## Branch Strategy

Project EKA follows a simplified trunk-based development model with short-lived feature branches.

### Persistent branches

| Branch | Purpose | Direct push |
|---|---|---|
| `main` | Production-ready code; tagged releases | No — PR only |
| `develop` | Integration branch; next release candidate | No — PR only |

### Short-lived branches

All work happens on branches cut from `develop` and merged back via pull request.

| Prefix | When to use | Example |
|---|---|---|
| `feature/` | New functionality | `feature/weaviate-adapter` |
| `fix/` | Bug fix | `fix/chunk-sequence-overflow` |
| `docs/` | Documentation only | `docs/update-architecture-diagram` |
| `refactor/` | Code restructuring with no behaviour change | `refactor/query-pipeline-extraction` |
| `test/` | Tests only | `test/archunit-domain-rules` |
| `chore/` | Build, CI, dependency updates | `chore/gradle-8-upgrade` |
| `hotfix/` | Critical production fix — cut from `main` | `hotfix/jwt-expiry-validation` |

### Branch naming rules

- Use **lowercase kebab-case** only
- Keep names concise and descriptive (under 50 characters)
- No personal names, ticket-only references without description, or vague names like `my-changes`

---

## Commit Message Conventions

Project EKA follows the [Conventional Commits](https://www.conventionalcommits.org/) specification. Every commit message must be machine-readable and human-meaningful.

### Format

```
<type>(<scope>): <short summary>

[optional body]

[optional footer(s)]
```

### Types

| Type | When to use |
|---|---|
| `feat` | A new feature or capability |
| `fix` | A bug fix |
| `docs` | Documentation changes only |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `test` | Adding or updating tests |
| `chore` | Build process, dependency, or CI changes |
| `perf` | Performance improvement |
| `style` | Formatting, whitespace — no logic change |
| `revert` | Reverting a previous commit |

### Scopes

Use the bounded context or layer as the scope:

`domain` · `application` · `infrastructure` · `api` · `security` · `ingestion` · `retrieval` · `generation` · `weaviate` · `postgres` · `ollama` · `ci` · `docs`

### Examples

```
feat(ingestion): add SemanticChunkingStrategy for PDF documents

fix(retrieval): correct hybrid search alpha parameter binding

docs(architecture): update MCP integration diagram

refactor(domain): extract MetadataFilter.Builder to inner class

test(archunit): add rule enforcing no Spring annotations in domain

chore(deps): upgrade Spring AI to 1.0.1
```

### Rules

- Summary line: **imperative mood**, present tense ("add" not "added", "fix" not "fixes")
- Summary line: **no period** at the end
- Summary line: **72 characters maximum**
- Body: explain **why**, not what (the diff shows what)
- Breaking changes: add `BREAKING CHANGE:` footer and `!` after type, e.g. `feat(api)!:`

---

## Pull Request Process

### Before opening a PR

- [ ] All tests pass locally: `./gradlew test`
- [ ] ArchUnit rules pass: `./gradlew test --tests HexagonalArchitectureTest`
- [ ] No new compiler warnings introduced
- [ ] Relevant documentation updated
- [ ] Branch is rebased onto the latest `develop`

### Opening the PR

1. Use the **pull request template** provided in `.github/PULL_REQUEST_TEMPLATE.md`
2. **Title** must follow the same Conventional Commits format as commit messages
3. **Description** must include:
   - What changed and why
   - Any architectural decisions made
   - How to test the change
   - Screenshots or diagrams for architectural changes
4. Mark as **Draft** while work is in progress — only mark Ready when genuinely ready for review
5. Request review from at least **one maintainer**

### Review expectations

- Reviewers aim to respond within **48 hours** on weekdays
- A PR requires **at least one approving review** before merge
- Address all review comments before requesting re-review
- Prefer **resolving** comments over **replying** once addressed

### Merging

- **Squash and merge** for feature branches (keeps `develop` history clean)
- **Merge commit** for `hotfix/` branches merging into `main` (preserves context)
- The PR author merges after approval, unless the reviewer merges directly
- Delete the branch after merge

---

## Coding Standards

### Java

- **Java 21** — use records, pattern matching, and sealed classes where they improve clarity; do not use preview features in production paths without team agreement
- **No magic numbers** — named constants or enum values only
- **No raw types** — all generics must be typed
- **No unused imports** — configure your IDE to auto-remove them
- **Null safety** — prefer `Optional` over returning `null` from public methods; use `@NonNull`/`@Nullable` annotations at public API boundaries

### Naming

- Classes: `UpperCamelCase`
- Methods and variables: `lowerCamelCase`
- Constants: `UPPER_SNAKE_CASE`
- Packages: `lowercase.dot.separated` — no underscores
- Ports (interfaces): noun-based, e.g. `DocumentRepository`, `LLMPort`, `EmbeddingPort`
- Adapters (implementations): `<Technology><Port>Adapter`, e.g. `WeaviateVectorAdapter`, `OllamaLLMAdapter`
- Use cases (interfaces): verb-based, e.g. `DocumentIngestionUseCase`, `QueryOrchestrationUseCase`

### Lombok

- **Allowed only on JPA entities** — not in the domain layer, not in application services
- Domain objects use plain Java factory methods (`create()`, `reconstitute()`)
- Do not use `@Data` on entities — use `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` explicitly

### Testing

- Every new class must have a unit test
- Infrastructure adapters must have integration tests using Testcontainers
- ArchUnit tests are not optional — do not suppress them
- Test method naming: `methodName_stateUnderTest_expectedBehaviour`
- No `@Disabled` tests committed to `main` or `develop` without a linked issue

### Comments

- Default to **no comments** — the code and naming should be self-explanatory
- Add a comment only when the **why** is non-obvious: a hidden constraint, a workaround for a known bug, or a subtle invariant
- No commented-out code — use version control

---

## Architecture Principles

All contributions must respect the architectural boundaries enforced by ArchUnit. A contribution that fails the hexagonal architecture test will not be merged.

### The non-negotiable rules

1. **Domain layer imports nothing** from `application`, `infrastructure`, `api`, `jakarta.persistence`, or `org.springframework`
2. **Application layer imports nothing** from `infrastructure` or `api`
3. **Infrastructure layer imports nothing** from `api`
4. **All AI calls go through ports** — no direct imports of `org.springframework.ai`, `io.weaviate`, or `org.apache.tika` above the infrastructure layer
5. **No database annotations in domain** — `@Entity`, `@Column`, `@Table` and all JPA annotations belong in the infrastructure layer only

### Before adding a new dependency

Ask: which layer does this belong to? If the answer is "domain" and the library is a framework or infrastructure concern, reconsider. Pure utility libraries (e.g., a date/time utility with no framework dependency) may be acceptable in the domain; everything else belongs in infrastructure or application.

### Adding a new provider (LLM, vector store, embedding model)

1. Define or reuse the relevant port interface in the domain
2. Create a new adapter in `infrastructure/` implementing that port
3. Register it as a Spring `@Component` with a `@ConditionalOnProperty` to allow switching between implementations
4. Add an integration test with Testcontainers or a WireMock stub
5. Update `docs/component-architecture.md` with the new provider in the pluggable extension points table

---

## Documentation Requirements

Documentation is a first-class deliverable in Project EKA. A feature is not complete until the relevant documentation is updated.

### When documentation is required

| Change type | Required documentation update |
|---|---|
| New port or adapter | Update `docs/component-architecture.md` pluggable extension points table |
| New bounded context or aggregate | Update `docs/logical-architecture.md` domain model section |
| Architectural decision | Add entry to Decision Log in `docs/roadmap.md` |
| New pipeline stage | Update `docs/component-architecture.md` pipeline section + sequence diagram |
| New API endpoint | Update OpenAPI specification |
| Phase completion | Update progress table in `README.md` and `docs/roadmap.md` |
| Security-relevant change | Update `SECURITY.md` if disclosure process is affected |

### Documentation standards

- **Plain English** — write for a senior engineer who has not read the codebase
- **Explain the why** — not just what the component does, but why it was designed this way
- **Include trade-offs** — every significant decision has a cost; document it
- **Keep diagrams in sync** — if the architecture diagram does not match the code, the diagram is wrong
- Markdown files use **GitHub Flavoured Markdown**
- Tables are preferred over bulleted lists for structured comparisons
- One blank line between sections; two blank lines between major sections separated by `---`

---

## Reporting Bugs

Open a GitHub Issue using the **Bug Report** template. Include:

- Java version, OS, and relevant dependency versions
- Steps to reproduce (minimal reproduction preferred)
- Expected behaviour
- Actual behaviour
- Relevant logs (sanitised — no credentials, no personal data)

**Do not open a public issue for security vulnerabilities.** See [SECURITY.md](SECURITY.md) for responsible disclosure.

---

## Suggesting Features

Open a GitHub Discussion or Issue using the **Feature Request** template. Include:

- The problem you are trying to solve (not just the solution)
- The use case and who benefits
- Whether this affects existing architectural boundaries
- Any alternative approaches considered

Major architectural changes (new bounded context, new infrastructure dependency, changes to port contracts) require an **Architecture Decision Record (ADR)** as part of the proposal. Discuss before implementing.

---

## Questions

If something in this guide is unclear, open a GitHub Discussion. We prefer public questions over private messages — the answer benefits everyone.
