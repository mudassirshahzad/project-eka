# Platform consumer smoke test

A deliberately minimal, **standalone** Gradle project that depends on `project-eka` the
way an external consumer does — by coordinate, resolved from a repository — and compiles
against EKA's domain ports.

It is not part of the root Gradle build (it is not in `settings.gradle`), because the
whole point is to resolve the *published artifact* rather than the project it was built
from. A composite build or a project dependency would resolve EKA's classes directly and
prove nothing about what consumers actually receive.

## What it guards

**Two real defects so far, neither of which any test inside EKA could have seen.** That track
record is the argument for keeping this project around.

**1. The published POM must carry resolvable dependency versions.**
`io.spring.dependency-management` writes its resolved versions into the POM's
`<dependencyManagement>` block but **not** into Gradle Module Metadata, and Gradle prefers
`.module` over `.pom`. Publishing both handed Gradle consumers a versionless graph that failed with
`Could not find org.postgresql:postgresql:.` — while the POM alone was perfectly fine.

**2. The library jar must not leak application-owned resources.**
It shipped EKA's `application.yml`, all 19 Flyway migrations at their default location, and
`META-INF/build-info.properties`. All three are discovered *by convention*, so a consumer inherited
them silently: a consumer would fail to start on EKA's missing `DB_PASSWORD`, report EKA's version
as its own, and — the serious one — apply EKA's 19 migrations to its own database, since
`spring.flyway.locations` defaults to `classpath:db/migration`.

Both shipped while every check in the main build was green, because EKA's tests compile against
EKA's own source tree and never see a packaged artifact.

## Running it

```bash
# from the repository root
gradle publishToMavenLocal
cd platform-smoke && gradle run
```

Expected final lines:

```
PACKAGING CHECK PASSED (no application.yml, no default-path migrations)
CONSUMER SMOKE TEST PASSED
```

Both assertions were confirmed to **fail** when the defect they guard is reintroduced — a guard
that has never been seen to fail is not known to work.

CI runs exactly this on every pull request (`platform-smoke` job in
`.github/workflows/build.yml`).
