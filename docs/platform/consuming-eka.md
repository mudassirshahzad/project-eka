# Consuming EKA as a library

EKA ships two artifacts from one build. They are not interchangeable:

| Artifact | File | What it is |
|---|---|---|
| **Library** | `project-eka-<version>.jar` | The dependency artifact. Plain jar, ~500 KB. This is what you depend on. |
| **Application** | `project-eka-<version>-boot.jar` | The executable EKA Assistant. ~145 MB, self-contained. Not a dependency. |

A sources jar (`project-eka-<version>-sources.jar`) is published alongside the library so
consumers can navigate into the domain model from an IDE.

> Gradle's default is the other way round — the boot jar takes the bare name and the
> library gets a `-plain` classifier. EKA swaps them deliberately, so that depending on
> `com.mudassirshahzad:project-eka` gives you a library rather than a 145 MB fat jar with a
> flattened copy of the entire dependency tree inside it.

---

## Adding the dependency

### Gradle

```groovy
repositories {
    mavenCentral()
    maven {
        url = 'https://maven.pkg.github.com/mudassirshahzad/project-eka'
        credentials {
            username = System.getenv('GITHUB_ACTOR')
            password = System.getenv('GITHUB_TOKEN')   // needs read:packages
        }
    }
}

dependencies {
    implementation 'com.mudassirshahzad:project-eka:<version>'
}
```

GitHub Packages requires authentication even for public packages — a personal access token
with `read:packages` is enough.

### Local development against an unreleased build

```bash
# in the EKA checkout
gradle publishToMavenLocal

# in the consumer, put mavenLocal() first
repositories { mavenLocal(); mavenCentral() }
```

---

## What you get

The published jar contains the whole application, but the part that is meaningful to a
consumer is the hexagon:

- **`domain/`** — the model (`TenantId`, `Document`, `Chunk`, `RetrievalResult`, …) and
  **every port interface**. Pure Java: no Spring, no JPA, no Spring AI, no Jackson. This
  purity is enforced by ArchUnit, not convention, so it stays true.
- **`application/`** — the orchestration services.
- **`infrastructure/`** — the shipped adapters (Weaviate, Postgres/BM25, Ollama, Tika).

A consumer can implement a port without inheriting an adapter:

```java
public final class MyRetrieval implements RetrievalPort {
    @Override
    public RetrievalResult retrieve(String queryText, TenantId tenantId,
                                    MetadataFilter filter, RetrievalOptions options) {
        ...
    }
}
```

`platform-smoke/` in this repository is exactly this, kept minimal and run in CI.

---

## Known limitations

These are real and deliberate. Read them before depending on EKA in anger.

**1. All POM dependencies are `runtime` scope.**
EKA declares everything as Gradle `implementation`, which Maven publication maps to
`runtime`. You get EKA's transitive dependencies on your *runtime* classpath, but not your
*compile* classpath. In practice this is a non-issue for a Spring Boot consumer (which
already compiles against Spring), and EKA's own classes — the ones you actually compile
against — are in the jar itself. It is resolved properly by the module split below.

**2. It is one jar, not four.**
Depending on EKA today means depending on all of it — Weaviate client, Tika parsers,
Postgres driver — even if you only want prompting. The
[platform blueprint](../analysis/eka-platform-blueprint.md) splits this into `eka-core`,
`eka-llm`, `eka-rag` and `eka-app`, at which point a consumer that only needs the LLM
gateway stops dragging in a vector store. Until then, budget for the full transitive set.

**3. Gradle Module Metadata is not published.**
Only the POM is. This is not an oversight — see below.

---

## Why no Gradle Module Metadata

EKA's versions come from Spring's `io.spring.dependency-management` plugin, which is also
what makes the security version overrides in `build.gradle` work. That plugin writes its
resolved versions into the generated POM's `<dependencyManagement>` block, but **not** into
Gradle Module Metadata.

Gradle prefers `.module` over `.pom` when both exist. Publishing both therefore handed
Gradle consumers a dependency graph with no versions at all:

```
> Could not find org.postgresql:postgresql:.
  Required by: root project : > com.mudassirshahzad:project-eka:0.8.4
```

— while the POM sitting next to it was perfectly correct. Nothing in EKA's own test suite
could detect this, because EKA compiles against its own source tree.

Publishing the POM alone is correct for both Maven and Gradle consumers: Gradle honours
`<dependencyManagement>` entries as constraints. The permanent fix is migrating to Gradle's
native `platform()` BOM support, which belongs with the module split (it would invalidate
the `ext['*.version']` security overrides, so the two changes have to happen together).

**`platform-smoke/` exists to make sure this never regresses silently again.**

---

## Releases and versions

The library is published to GitHub Packages by CI, on **release tags only** (`v*`), gated
behind a green build. Branch pushes deliberately do not publish: the project version is a
fixed release number and GitHub Packages rejects re-publishing one, so publishing on every
push would fail on the second commit after each release.

`project.version` in `build.gradle` is the single source of truth for the jar, the git tag,
the GitHub Release and `/actuator/info` (ADR EX03).
