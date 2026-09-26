# Dependency Roadmap

**Date:** 2026-09-26 · **Repository state:** v0.8.4, `main` · **Build:** green (813 tests, 0 failures)

Audit of every dependency Project EKA declares or resolves, with a recommended target version for
each. **No upgrade is implemented by this document** — it is the decision record that the upgrade
work will be executed against.

Every version in this document was resolved empirically, not assumed:

- **Current** — `gradle dependencies --configuration runtimeClasspath` (resolved, post-conflict-resolution
  version, not the requested one).
- **Latest** — `maven-metadata.xml` from `repo1.maven.org` (authoritative; Maven Central's solr
  search endpoint returns a stale ordering and was not used). **Caveat learned the hard way:** the
  `<release>` tag points at the newest version *overall*, which for Spring AI is a 2.x milestone —
  reading it alone hides the stable in-major lines. Always filter the full `<version>` list per
  line (1.0.x **and** 1.1.x, 2.x **and** 2.9.x) before concluding what the in-line target is.
- **Advisories** — GitHub Dependabot alerts via `gh api`, **paginated**. This matters: the first
  unpaginated page reports 27 alerts; the true open count is **104**.

---

## 1. Headline

| | |
|---|---|
| Open advisories | **104** — 8 critical, 40 high, 43 medium, 13 low, across 30 packages |
| Direct declarations | 6 version-pinned (`springAiVersion`, `jjwtVersion`, `tikaVersion`, `archunitVersion`, `springdocVersion`, Boot plugin) |
| Root cause of ~95% of advisories | **Spring Boot 3.5.0 is 16 patch releases behind 3.5.16.** Almost nothing here is EKA's own dependency choice — it is one stale BOM pinning ~30 transitive libraries to their May 2025 versions |
| Single highest-value action | **Boot 3.5.0 → 3.5.16 + an explicit Tomcat override.** Tomcat alone accounts for 6 of the 8 criticals and 12 of the 40 highs |
| Open Dependabot PRs | 2, **both red**, both proposing major jumps (see §5) |

**The important structural finding:** EKA has no dependency *sprawl* problem and no bad dependency
*choices*. It has one **stale-BOM** problem. That is good news — the remediation is a version bump
and a handful of pins, not a migration.

---

## 2. Security-critical — act first

These are grouped by the **action** that closes them, because acting per-advisory would mean 104
separate changes for what is really four.

### 2.1 Spring Boot 3.5.0 → 3.5.16 — closes ~70 advisories

A **patch-line** move inside 3.5.x. No API changes, no deprecations, no configuration migration.
This is the single cheapest, highest-yield change available.

What the BOM upgrade carries with it (verified by diffing `spring-boot-dependencies` 3.5.0 vs 3.5.16):

| Managed library | 3.5.0 | 3.5.16 | Advisories closed |
|---|---|---|---|
| `spring-framework` | 6.2.7 | **6.2.19** | webmvc, webflux, web, core, expression — ~30 |
| `spring-security` | 6.5.0 | **6.5.11** | core + web, incl. 1 critical |
| `spring-data-bom` | 2025.0.0 | **2025.0.13** | spring-data-commons ×4 (2 high) |
| `micrometer` | 1.15.0 | **1.15.12** | 2 high (HTTP + gRPC instrumentation DoS) |
| `jackson-bom` | 2.19.0 | **2.21.4** | jackson-databind ×5, jackson-core ×2 |
| `tomcat` | 10.1.41 | 10.1.55 | **partial — see 2.2** |
| `postgresql` | 42.7.5 | **42.7.11** | 3 high |
| `logback` | 1.5.18 | **1.5.34** | 4 |
| `assertj` | 3.27.3 | **3.27.7** | 1 high (test-scope only) |

**Risk:** very low. Patch releases within a supported Spring Boot line are API-stable by policy.
**Effort:** ~15 minutes plus one full CI run.
**Recommended target: `3.5.16`.**

### 2.2 Tomcat override → 10.1.60 — closes 6 critical + 12 high

**This is the finding that a plain Boot bump does not fix, and the most important line in this document.**

Boot 3.5.16 ships Tomcat **10.1.55**. Three critical advisories
(`GHSA-h3x4-894j-xpx5` FORM-auth incorrect authorization, `GHSA-9xv2-5v5q-p794` DIGEST
capture-replay auth bypass, `GHSA-gcx9-497g-6cp6` improper access control) are first patched in
**10.1.58**. Upgrading Spring Boot alone therefore leaves EKA's most severe advisories open.

Note also that **10.1.58 itself is not published to Maven Central** — the published sequence goes
10.1.57 → 10.1.59 → 10.1.60. The target must be **10.1.59 or later**; specify `10.1.60`.

```groovy
// Spring Boot 3.5.16's BOM pins Tomcat 10.1.55; three critical CVEs need >= 10.1.58.
// A patch-level override inside the 10.1.x line Boot already targets.
ext['tomcat.version'] = '10.1.60'
```

**Risk:** low — patch-level, same 10.1.x line Boot 3.5.x is built against.
**Do not skip this in favour of "wait for the next Boot release".** It is a one-line pin and it is
where the criticals live.

### 2.3 Spring AI 1.0.0 → 1.0.9 — closes 1 critical + 5 high

`spring-ai-vector-store` carries **1 critical + 2 high**; `spring-ai-client-chat` 2 high;
`spring-ai-model` 1 high. All are first patched at 1.0.4–1.0.7.

Two in-major targets exist, and the choice matters:

| Target | Kind | Closes the advisories | Note |
|---|---|---|---|
| `1.0.9` | pure patch | ✅ | Zero-risk fallback. But 1.0.x is the **older** line and will stop receiving fixes once 1.1.x is the maintained one |
| **`1.1.8`** | minor, same major | ✅ | **Recommended.** The current maintained 1.x line, built against Boot 3.5.x |

**Recommended target: `1.1.8`**, with `1.0.9` as the fallback if the minor bump surfaces any API
friction. Both stay inside major 1 — neither is the 2.x jump Dependabot proposes (§5). Taking
`1.0.9` closes today's advisories but leaves EKA on a line that is about to stop being patched,
which is how this situation recurs.

**Risk:** low either way. **Effort:** ~15 min + CI.

### 2.4 Four remaining transitive pins

Left open after 2.1–2.3, because Boot 3.5.16's BOM does not manage them to a patched version:

| Package | Resolved | Needs | Target | Severity | Comes via |
|---|---|---|---|---|---|
| `httpcore5`, `httpcore5-h2` | 5.3.4 | 5.4.3 | **5.4.4** | 2 high | Weaviate / Spring AI |
| `commons-lang3` | 3.17.0 | 3.18.0 | **3.18.0** | 1 medium | Tika |
| `spring-retry` | 2.0.12 | 2.0.13 | **2.0.13** | 1 medium | Spring AI |
| `log4j-api` | 2.24.3 | 2.25.5 | **2.25.5** | 1 medium | Tika (API jar only) |

All four are patch/minor moves on libraries EKA does not call directly. `httpcore5` 5.3 → 5.4 is a
minor bump and the only one of the four warranting a deliberate CI check rather than a blind pin.

### 2.5 Two that should NOT be pinned yet

| Package | Resolved | Needs | Why not now |
|---|---|---|---|
| `grpc-netty-shaded` | 1.68.2 | 1.75.0 | 1 high. Arrives via the Weaviate client, which pins its own gRPC stack. A 7-minor-version jump under a client that has opinions about gRPC is a real compatibility risk for **no** benefit EKA can verify without a live Weaviate. Upgrade **with** the Weaviate client, not ahead of it. |
| `httpclient5` | 5.4.4 | 5.6.3 | 1 medium only. 5.4 → 5.6 is two minors. Bundle it with the `httpcore5` work above and let one CI run cover both. |

### 2.6 One advisory that is not applicable

`artemis-project` / `GHSA-ggg2-9786-hwc8` (Boot predictable temp directory in **Artemis**
auto-configuration). **ActiveMQ Artemis is not on EKA's runtime classpath at all** — verified
against the resolved dependency tree. The auto-configuration never activates. The alert is
technically correct about the BOM and practically irrelevant to this application; it closes
incidentally with 2.1. Recorded here so it is not re-investigated later.

---

## 3. Direct dependencies — full audit

| Dependency | Current | Latest | Gap | Security | Compatibility | Effort | **Target** |
|---|---|---|---|---|---|---|---|
| `org.springframework.boot` (plugin + BOM) | 3.5.0 | 4.2.0-M2 | major behind; **16 patches** behind in-line | **~70 advisories** | 3.5.16 drop-in. 4.x is a breaking major | 15 min | **3.5.16** |
| `spring-ai-bom` | 1.0.0 | 2.1.0-M1 (2.0.1 stable) | major behind; a full **minor** behind in-line (1.1.8) | **1 critical, 5 high** | 1.1.8 targets Boot 3.5.x; 2.x needs Boot 4 | 15 min | **1.1.8** (fallback 1.0.9) |
| `tika` | 4.0.0 | 4.0.0 | current | none | migrated and green | — | **4.0.0** (hold) |
| `jjwt` | 0.13.0 | 0.13.0 | current | none | — | — | **0.13.0** (hold) |
| `archunit-junit5` | 1.5.0 | 1.5.1 | 1 patch | none | test-only, drop-in | 5 min | **1.5.1** |
| `springdoc-openapi` | 2.7.0 | 3.1.1 | major behind; **2.9.1** available in-line | none | 3.x requires Boot 4 — blocked. 2.9.1 is built against Boot 3.5.x | 10 min | **2.9.1** |
| `java` toolchain | 21 | 21 LTS | current | — | — | — | **21** (hold) |
| `gradle` | 8.12 | 9.x | major behind | none | see §6 | — | **8.12** for now |

---

## 4. Grouped verdict

### ✅ Safe to upgrade now

| Change | Closes | Risk | Effort |
|---|---|---|---|
| Spring Boot 3.5.0 → **3.5.16** | ~70 advisories | very low | 15 min + CI |
| `ext['tomcat.version'] = '10.1.60'` | **6 critical, 12 high** | low | 5 min + CI |
| Spring AI 1.0.0 → **1.1.8** | 1 critical, 5 high | low | 15 min + CI |
| springdoc 2.7.0 → **2.9.1** | — (hygiene; stays on Boot 3.5.x) | low | 10 min + CI |
| ArchUnit 1.5.0 → **1.5.1** | — (hygiene) | very low | 5 min |
| Pin `commons-lang3` 3.18.0, `spring-retry` 2.0.13, `log4j-api` 2.25.5 | 3 medium | low | 10 min + CI |

**Together: one commit, ~1 hour including CI, and 8 of 8 criticals plus ~37 of 40 highs close.**

### ⏳ Upgrade later (deliberate, needs its own verification)

- `httpcore5` / `httpcore5-h2` → 5.4.4 **and** `httpclient5` → 5.6.4 — one combined change, one CI run. 2 high + 1 medium.
- `grpc-netty-shaded` → 1.75.0 — only alongside a Weaviate client upgrade (§2.5).
- Gradle 8.12 → 9.x — no advisory; do it only as the prerequisite it is (§6), not for its own sake.

### 🚫 Blocked

| Change | Blocked by |
|---|---|
| Spring Boot 4.x | Breaking major. Needs Spring AI 2.x **and** springdoc 3.x **and** a Gradle upgrade, together. Not a dependency bump — a migration milestone |
| Spring AI 2.x | Requires Boot 4 |
| springdoc 3.x | Requires Boot 4 |

### 🔴 Security critical

Everything in §2.1–2.4. The 6 critical + 12 high Tomcat advisories are the ones that matter most,
and they are **not** closed by a Boot bump alone (§2.2).

---

## 5. Why both open Dependabot PRs are red

Neither should be merged as-is; neither should be closed as noise — each is pointing at something real.

| PR | Proposes | Status | Action |
|---|---|---|---|
| [#3](https://github.com/mudassirshahzad/project-eka/pull/3) | Boot 3.5.0 → **4.1.1**, Spring AI 1.0.0 → **2.0.1** | ❌ CI red: `An exception occurred applying plugin request [id: 'org.springframework.boot', version: '4.1.1']` | **Close in favour of the in-line 3.5.16 / 1.1.8 upgrade.** A grouped double-major jump cannot be validated in one PR |
| [#7](https://github.com/mudassirshahzad/project-eka/pull/7) | springdoc 2.7.0 → **3.1.1** | ❌ CI red | **Close.** springdoc 3.x requires Boot 4 — take **2.9.1** instead, which is in-line and current |

**Configuration improvement worth making:** `.github/dependabot.yml` groups all
`org.springframework*` + `io.spring*` into one `spring` group with no version ceiling, which is
exactly how PR #3 became an unmergeable double-major. Adding
`update-types: [minor, patch]` to the `spring` group (and letting majors be raised deliberately,
as milestones) would make Dependabot produce the mergeable in-line upgrades instead of red ones.
This is a **CI-configuration** change, not a dependency upgrade, and is in scope for a separate task.

---

## 6. Gradle 8.12 and the Boot 4 path

The Gradle pin is worth stating precisely, because it is easy to record as a blocker when it is
really a prerequisite. Spring Boot 4's Gradle plugin requires a newer Gradle than 8.12; EKA has
**no committed wrapper** (deliberate — CI provisions 8.12 via `gradle/actions/setup-gradle`), so
"upgrade Gradle" means editing one pinned version in `.github/workflows/build.yml`, the
`Dockerfile` build stage (`gradle:8.12-jdk21`), and `gradle/wrapper/gradle-wrapper.properties`.

**Sequence, when Boot 4 is actually wanted:** Gradle → Boot 4 → Spring AI 2 → springdoc 3, as one
milestone with its own planning session. **Not now.** Nothing in §2 needs it, and the entire
security position can be fixed on Gradle 8.12 / Boot 3.5.16.

---

## 7. Recommended execution order

1. **Boot 3.5.16 + Tomcat 10.1.60 + Spring AI 1.1.8 + springdoc 2.9.1 + the three medium pins** — one commit. Closes 8/8 criticals, ~37/40 highs.
2. **Close Dependabot PRs #3 and #7** with a comment pointing at this document.
3. **Constrain the `spring` Dependabot group to minor/patch** so future PRs are mergeable.
4. **ArchUnit 1.5.1** — hygiene, bundle with any convenient commit.
5. **`httpcore5` + `httpclient5`** — one deliberate change with its own CI run.
6. **Re-audit.** `gh api .../dependabot/alerts --paginate` and confirm the open count. Expect single digits.
7. **Gradle → Boot 4 migration** — a future milestone, its own planning session, explicitly out of scope here.

---

## 8. Standing rule this audit followed

**Latest is not the target.** Major versions were recommended against in every case they appeared
(Boot 4, Spring AI 2, springdoc 3, Gradle 9), because in every case the *entire* security benefit
was available on the current minor line at a fraction of the risk. Tika 4.0.0 is the one major
already adopted, and it was adopted because it was current and verified green — not because it was
newest.

The corollary is the finding in §2.2: staying in-line is right, but it is **not automatically
sufficient**. Boot 3.5.16 leaves three critical Tomcat CVEs open, and only an explicit pin closes
them. Prefer patch/minor — then verify the result against the advisory list rather than assuming
the bump did the job.
