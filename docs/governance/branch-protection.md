# Branch Protection — Recommended Configuration

**Status:** Not yet applied. GitHub branch protection is a repository setting, not something
this codebase can configure from within itself — `gh api repos/.../branches/main/protection`
confirmed `main` is currently unprotected (v0.6.1 audit). This document specifies exactly what
to configure and why, so it can be applied deliberately via the GitHub UI or `gh api` rather than
guessed at.

## Required status checks

- **Check name:** `Build, test, ArchUnit` (the job defined in
  [`.github/workflows/build.yml`](../../.github/workflows/build.yml), introduced v0.6.1).
- **Require branches to be up to date before merging:** yes — a stale branch can pass CI against
  an old `main` and still merge a real conflict/regression.

Once the workflow has run at least once against a PR, its check name becomes selectable under
**Settings → Branches → Branch protection rules → Require status checks to pass**.

## Required reviews

- **Require a pull request before merging:** yes.
- **Required approving reviews:** 0 for a single-maintainer repository (current state); raise to
  1+ the moment a second contributor joins. Recording this now avoids a silent gap being
  discovered later — a 0-review requirement is a deliberate, documented choice, not an oversight.
- **Dismiss stale approvals on new commits:** yes, once reviews are required.

## Push restrictions

- **Do not allow direct pushes to `main`.** Every change — including the maintainer's own —
  goes through a PR, so the required status check above actually gates every change, not just
  contributions from others.
- **Do not allow force pushes to `main`.**
- **Do not allow branch deletion of `main`.**

## Merge policy

- **Allowed merge strategy:** squash merge (keeps `main` history one commit per change, matching
  this repository's existing commit-message discipline) or merge commit — either is acceptable;
  avoid enabling rebase-merge alongside the other two to prevent inconsistent history shapes.
- **Require linear history:** optional; not required if squash-only is enforced, since squash
  merges are inherently linear.

## Why this wasn't applied automatically

Branch protection is a live GitHub repository setting change, not a file in this repository.
Per this milestone's own instruction ("do not assume GitHub settings can be modified from inside
the repository") and this project's general operating discipline around actions with an
external, shared blast radius, it is documented here for the repository owner to apply
deliberately rather than applied unilaterally.

## Apply via `gh` CLI (reference — run manually, not part of this milestone)

```bash
gh api repos/mudassirshahzad/project-eka/branches/main/protection \
  --method PUT \
  --field required_status_checks[strict]=true \
  --field 'required_status_checks[contexts][]=Build, test, ArchUnit' \
  --field enforce_admins=true \
  --field required_pull_request_reviews[required_approving_review_count]=0 \
  --field restrictions=null \
  --field allow_force_pushes=false \
  --field allow_deletions=false
```
