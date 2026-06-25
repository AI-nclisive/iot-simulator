# Contributing

This repo is built in parallel by multiple people and agents via feature branches
and pull requests. These rules keep that smooth.

## Prerequisites
- JDK 25 (toolchain target). Gradle runs via the committed wrapper (`./gradlew`).
- Docker (Docker Desktop / colima) — required for Testcontainers integration
  tests. Without Docker those ITs skip locally; CI always runs them.

## Build & test
```bash
./gradlew build        # compile + all tests (unit + ITs + ArchUnit)
```
Always run this and confirm it is green before opening a PR.

## Task IDs
- Every task has a project-wide ID and area marker, named **`IS-XXX [AREA] short name`** —
  `[AREA]` is `[BE]` (backend), `[FE]` (frontend) or `[SDLC]` (repo/process).
- Catalog: `backend-specs/TASKS.md` (with a legacy `BE-*`/`SDLC-*` → `IS-XXX` crosswalk).
- Reuse the ID everywhere: branch `feat/IS-038-...`, issue `IS-038 short name` (area via the form's **Area** field), PR `Implements: IS-038`.

## Branching
- Branch off `master`. One task per branch/PR.
- Name: `feat/IS-123-short-slug`, `fix/...`, `docs/...`, `chore/...`, `test/...`.

## Commits & PRs
- **Language: write all GitHub text in English** — commit messages, PR titles and
  descriptions, issue text, and every comment (including replies to review findings).
- Conventional Commits: `type(scope): subject` (e.g. `feat(schema): ...`).
- Reference the task in the PR: `Implements: IS-123`, `Closes: #<issue>`.
- Keep PRs small and focused; fill in the PR template checklist.
- Merge strategy: **squash merge**, linear history. CI must be green and at least
  one approving review is required.

## AI review loop
Every PR is reviewed automatically by an advisory Claude reviewer (IS-112; see
`.github/workflows/claude-review.yml`). It posts **inline comments on the specific
lines** it thinks need rework (prefixed `[blocking]` / `[nit]`) and **one top-level
verdict comment** — `## Claude review: ✅ Mergeable` or `## Claude review:
❌ Changes requested` with the reasons. It is advisory and does not gate merge — the
required check stays `build`.

After opening (or updating) a PR:
1. **Wait for the verdict.**
2. For each finding, either **fix it**, or **reply on the comment** explaining why
   the current approach is correct and the better choice. Then **push to the same
   branch** — each push re-triggers the review.
3. **Wait for the new verdict** and repeat.

The PR is ready for **human review** — move the task to **In review** on the board —
once either:
- the verdict is `✅ Mergeable` (no remaining blocking findings), or
- **3 review rounds** have completed without a clean verdict (then summarize the
  still-open points for the human reviewer in the PR description).

While iterating on review findings the task stays **In Progress**; it becomes
**In review** only when one of the above is met. The board `Status` moves to
**Done** only when the PR is merged — the catalog checkbox itself is flipped
inside the implementation PR (see "Task tracking").

## Definition of Done
- `./gradlew build` green (tests added/updated for the change).
- No secrets/credentials/PKI committed; secrets come from env/secret store.
- Generated code (jOOQ/proto) stays under `build/` — never committed.
- Public behavior changes reflected in OpenAPI and, if needed, the specs.

## Task tracking
- `backend-specs/TASKS.md` / `frontend/docs/UI_TASKS.md` are the task
  **catalogs** (IS-/UI-IDs). **Live `In Progress` / `In review` status lives in
  GitHub Issues/Project** keyed by ID — don't track those by editing catalog
  checkboxes.
- **Marking a task Done:** flip its catalog checkbox to `[x]` ✅ **in the same PR
  that implements it** — this keeps the catalog current without a separate
  catalog-only PR, which branch protection on `master` would otherwise require.
  Move the **board `Status` to Done and close the issue only after that PR is
  merged**. Each PR edits only its own task's line, so conflicts are rare; only
  the aggregate snapshot count line is periodically retallied.
- File tasks with the **Backend task** issue form; labels are defined in
  `.github/labels.yml`. The Project board (live status by IS-ID) is an admin
  one-time setup: `gh project create` (needs the `project` token scope) or create
  it in the GitHub UI with a `Task ID` field, an `Area` (BE/FE/SDLC) field, and a status column.

## Parallel-work conventions
- **Flyway migrations**: never reuse a version number. Two open PRs adding
  `V7__...` will both merge and break ordering. Use the next free `Vn` only if you
  are sure it is unique, otherwise use a timestamped version
  (`V20260623_1530__name.sql`). Migrations are append-only.
- **Modules**: prefer changes scoped to one Gradle module to reduce conflicts.
- **Versions**: add/bump dependencies only in `gradle/libs.versions.toml`.

## Governance
- `SPEC.md`, `ARCHITECTURE.md`, `STACK.md`, `backend-specs/` change only with
  prior owner approval — propose first (see `AGENTS.md`). No new dependency
  without approval.

## Branch protection (repo admin)
Applied configuration record: [`.github/OWNER_SETUP.md`](.github/OWNER_SETUP.md). Requires
**admin** on the repo. `master` is protected (status check `build` = the CI job name):
```bash
gh api -X PUT repos/AI-nclisive/iot-simulator/branches/master/protection \
  --input - <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["build"] },
  "enforce_admins": false,
  "required_pull_request_reviews": { "required_approving_review_count": 1 },
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "restrictions": null
}
JSON
```
Also, in repo Settings: allow **squash merging only** and enable
"Automatically delete head branches". (Set `enforce_admins` to `true` later for a
stricter policy once more than one approver is available.)
