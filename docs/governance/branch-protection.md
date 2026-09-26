# Branch Protection — `main`

**Status: APPLIED — 2026-09-26.** Verified live via
`GET /repos/mudassirshahzad/project-eka/branches/main/protection` (previously `404 Branch not
protected`). This closes the branch-protection half of the Phase 7 exit gate (ADR GOV08/GOV09).

> **Phase 7 is still not complete.** Branch protection was **one of two** open exit criteria. The
> other — a measurable relevance improvement on a real evaluation set (ADR RQ07) — remains open, so
> the `Phase 7` GitHub milestone stays open. See [`.claude/PROJECT_STATE.md`](../../.claude/PROJECT_STATE.md).

Everything below was applied with the GitHub CLI. **No step required manual UI interaction.**

---

## 1. Applied configuration

### Branch protection rule — `main`

| Setting | Value | Requirement it satisfies |
|---|---|---|
| Require a pull request before merging | ✅ | Pull Requests required |
| Required approving reviews | **0** | See §3 — deliberate for a single maintainer |
| Dismiss stale approvals on new commits | ✅ | — |
| Require status checks to pass | ✅ | CI required |
| → `Build, test, ArchUnit` | ✅ required | Build required **and** ArchUnit required |
| → `Docker image build` | ✅ required | Docker build required |
| → `Dependency review (SCA)` | ✅ required | CI required (the security gate, ADR CG01) |
| Require branches to be up to date before merging (`strict`) | ✅ | A stale branch cannot pass CI against an old `main` and still merge a regression |
| Require conversation resolution | ✅ | — |
| Require linear history | ✅ | Linear history |
| Include administrators (`enforce_admins`) | ✅ | Include administrators |
| Allow force pushes | ❌ | Block force push |
| Allow deletions | ❌ | Block branch deletion |
| Lock branch | ❌ | — |

### Repository merge settings

| Setting | Value | Requirement it satisfies |
|---|---|---|
| Allow squash merging | ✅ | Squash merge enabled |
| Allow merge commits | ❌ | Merge commits disabled |
| Allow rebase merging | ❌ | See §3 |
| Allow auto-merge | ✅ | Auto merge enabled |
| Automatically delete head branches | ✅ | Hygiene — pairs with a PR-only workflow |
| Allow branch update ("Update branch" button) | ✅ | Makes the `strict` requirement above workable without a local rebase |

---

## 2. Which status checks are required — and one that deliberately is not

`.github/workflows/build.yml` defines four jobs. Only three are required, and the exclusion matters:

| Job | Runs on | Required? |
|---|---|---|
| `Build, test, ArchUnit` | PR + push | ✅ yes |
| `Docker image build` | PR + push (`needs: build`) | ✅ yes |
| `Dependency review (SCA)` | **PR only** (`if: github.event_name == 'pull_request'`) | ✅ yes |
| `Submit dependency graph` | **push only** (`if: github.event_name == 'push'`) | ❌ **no — and it must stay that way** |

**`Submit dependency graph` must never be added as a required check.** It is gated to `push`
events, so it never runs on a pull request. Requiring it would leave every PR permanently waiting
on a check that cannot start — a self-inflicted deadlock on every future merge. Recorded here
because the failure mode is non-obvious and the job name looks like a natural thing to require.

`Dependency review (SCA)` is safe to require despite reporting `skipped` on push events: GitHub
treats a skipped required check as satisfied, and it runs for real on every pull request. It fails
only on dependencies a PR *newly introduces* at high-or-worse severity — it does not fail on the
repository's existing advisory backlog (see [`../analysis/dependency-roadmap.md`](../analysis/dependency-roadmap.md)).

---

## 3. Two deliberate choices

**Required approving reviews = 0.** With `enforce_admins` enabled, a non-zero review requirement
would lock the sole maintainer out of their own repository entirely — GitHub does not let you
approve your own pull request, so `1` plus admin enforcement means nothing can ever merge. `0` still
forces every change through a pull request and still gates it on all three status checks; it only
drops the human-approval step that a single-maintainer project cannot satisfy. **Raise this to 1 the
moment a second contributor joins** — that is the only change needed, and `dismiss_stale_reviews` is
already enabled for it.

**Rebase merging disabled.** With squash-only, `main` gets exactly one commit per change and
`required_linear_history` is satisfied by construction. Leaving rebase enabled alongside squash
permits two different history shapes for no benefit.

---

## 4. Reproducing this configuration

Both commands are idempotent — safe to re-run to reassert the intended state.

```bash
# Branch protection rule
cat > protection.json <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Build, test, ArchUnit", "Docker image build", "Dependency review (SCA)"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 0,
    "require_last_push_approval": false
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": true,
  "lock_branch": false,
  "allow_fork_syncing": false
}
JSON

gh api -X PUT repos/mudassirshahzad/project-eka/branches/main/protection --input protection.json

# Repository merge settings
gh api -X PATCH repos/mudassirshahzad/project-eka \
  -F allow_squash_merge=true \
  -F allow_merge_commit=false \
  -F allow_rebase_merge=false \
  -F allow_auto_merge=true \
  -F delete_branch_on_merge=true \
  -F allow_update_branch=true
```

`"restrictions": null` is required, not optional: push restrictions (restricting *who* may push)
are an organization-only feature and the API rejects any other value on a user-owned repository.
`enforce_admins` is what actually stops direct pushes here, including the owner's.

### Verify

```bash
gh api repos/mudassirshahzad/project-eka/branches/main/protection \
  --jq '{checks: .required_status_checks.contexts,
         strict: .required_status_checks.strict,
         admins: .enforce_admins.enabled,
         linear: .required_linear_history.enabled,
         force_push: .allow_force_pushes.enabled,
         deletions: .allow_deletions.enabled}'
```

---

## 5. Checking it in the GitHub UI

No UI step is needed to *apply* any of this. For visual confirmation:

1. Go to **https://github.com/mudassirshahzad/project-eka**
2. Click **Settings** (top-right of the repository nav bar).
3. In the left sidebar, click **Branches**.
4. Under **Branch protection rules**, click the **`main`** rule → the checkboxes mirror §1.
5. For merge settings: left sidebar → **General** → scroll to **Pull Requests** → confirm only
   **Allow squash merging** is ticked, and **Allow auto-merge** and
   **Automatically delete head branches** are ticked.

---

## 6. What this changes day to day

`main` is now **push-protected for everyone, the owner included**. The workflow becomes:

```bash
git checkout -b <type>/<short-description>
# ... commit ...
git push -u origin <branch>
gh pr create --fill
gh pr merge --squash --auto    # auto-merge once all three checks pass
```

A direct `git push origin main` now fails with
`GH006: Protected branch update failed ... Required status checks must pass`. That is the rule
working, not a misconfiguration.

**One consequence worth planning for:** release tagging (step 9 of the canonical Release Workflow,
ADR GOV02) is unaffected — tag protection is separate and was not enabled — but every
documentation-sync and version-alignment commit in steps 4–6 now needs a pull request. Budget one
PR per release rather than a series of direct pushes.
