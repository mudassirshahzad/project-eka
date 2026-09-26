# Platform consumer smoke test

A deliberately minimal, **standalone** Gradle project that depends on `project-eka` the
way an external consumer does — by coordinate, resolved from a repository — and compiles
against EKA's domain ports.

It is not part of the root Gradle build (it is not in `settings.gradle`), because the
whole point is to resolve the *published artifact* rather than the project it was built
from. A composite build or a project dependency would resolve EKA's classes directly and
prove nothing about what consumers actually receive.

## What it guards

The published POM must carry resolvable dependency versions. This caught a real defect:
`io.spring.dependency-management` writes its resolved versions into the POM's
`<dependencyManagement>` block but **not** into Gradle Module Metadata, and Gradle prefers
`.module` over `.pom`. Publishing both handed Gradle consumers a versionless graph that
failed with `Could not find org.postgresql:postgresql:.` — while the POM alone was
perfectly fine. Nothing in EKA's own build could have detected that.

## Running it

```bash
# from the repository root
gradle publishToMavenLocal
cd platform-smoke && gradle run
```

Expected final line: `CONSUMER SMOKE TEST PASSED`.

CI runs exactly this on every pull request (`platform-smoke` job in
`.github/workflows/build.yml`).
