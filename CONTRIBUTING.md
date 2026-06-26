# Contributing

This repo is built in parallel by multiple people and agents via feature branches
and pull requests. These rules keep that smooth.

## Prerequisites
- JDK 25 (toolchain target). Gradle runs via the committed wrapper (`./gradlew`).
- Node.js 20 for the frontend. Run `nvm use` from the repo root if you use
  nvm/fnm/asdf; the expected version is recorded in `.nvmrc`.
- Docker (Docker Desktop / colima) — required for Testcontainers integration
  tests. Without Docker those ITs skip locally; CI always runs them.

## Build & test
```bash
./gradlew build        # compile + all tests (unit + ITs + ArchUnit)
npm ci                 # install locked frontend dependencies
npm run typecheck      # frontend TypeScript check
npm run build          # production frontend build
```
Always run this and confirm it is green before opening a PR.

## Task IDs
- Backend and repo/process tasks use **`IS-XXX [AREA] short name`** from
  `backend-specs/TASKS.md`; `[AREA]` is `[BE]` or `[SDLC]`.
- Frontend tasks use **`UI-XXX [FE] short name`** from
  `frontend/docs/UI_TASKS.md`.
- Reuse the ID everywhere: branch `feat/IS-038-...` or `feat/UI-022-...`, issue
  `IS-038 short name` / `UI-022 short name` (area via the form's **Area** field),
  PR `Implements: IS-038` or `Implements: UI-022`.

## Branching
- Branch off `master`. One task per branch/PR.
- Name: `feat/IS-123-short-slug`, `fix/...`, `docs/...`, `chore/...`, `test/...`.

## Commits & PRs
- **Language: write all GitHub text in English** — commit messages, PR titles and
  descriptions, issue text, and every comment (including replies to review findings).
- Conventional Commits: `type(scope): subject` (e.g. `feat(schema): ...`).
- Reference the task in the PR: `Implements: IS-123` / `Implements: UI-123`,
  `Closes: #<issue>`.
- Keep PRs small and focused; fill in the PR template checklist.
- Merge strategy: **squash merge**, linear history. CI must be green and at least
  one approving review is required.
- **Arm auto-merge when you open the PR**: `gh pr merge <n> --auto --squash`. GitHub
  then merges the PR automatically once branch protection is satisfied — i.e. the
  Claude reviewer's **APPROVE** plus a green `build` — with no manual merge click.
  (Native auto-merge is enabled on the repo; see `.github/OWNER_SETUP.md`.)

## AI review loop
Every PR is reviewed automatically by a Claude reviewer (IS-112; see
`.github/workflows/claude-review.yml`). It posts **inline comments on the specific
lines** it thinks need rework (prefixed `[blocking]` / `[nit]`) and **one top-level
verdict comment** — `## Claude review: ✅ Mergeable` or `## Claude review:
❌ Changes requested` with the reasons. It then submits a **formal GitHub review**
matching that verdict: **APPROVE** when nothing blocks, **REQUEST_CHANGES** otherwise.
This gates merge: the APPROVE supplies branch protection's required approving review,
and a REQUEST_CHANGES blocks merge until a later push earns an APPROVE. The required
status check stays `build`.

By the time the review runs the task is already **In review** (moved there when the
PR was opened — see "Task tracking"). Resolving the review is part of finishing the
task, not an optional follow-up. After opening (or updating) a PR:
1. **Wait for the verdict** and the inline comments to land.
2. For each finding, either **fix it**, or **reply on the comment** explaining why
   the current approach is the better choice; mark the thread **resolved** once it is
   addressed. Then **push to the same branch** — each push re-triggers the review.
3. **Wait for the new verdict** and repeat until either:
   - the verdict is `✅ Mergeable` with **no unresolved comments** — the reviewer's
     **APPROVE** then lands and, with auto-merge armed and `build` green, the PR
     squash-merges itself, or
   - **3 review rounds** have completed (then summarize any still-open points for the
     human reviewer in the PR description and leave each thread resolved-with-rationale).

**A feature is finished only when every reviewer comment is resolved** — fixed or
answered with a rationale — and `./gradlew build` is green. Do not consider the work
done, or walk away from the PR, while review threads are still open. The board
`Status` moves to **Done** only when the PR is merged; the catalog checkbox itself is
flipped inside the implementation PR (see "Task tracking").

## Definition of Done
- `./gradlew build` green (tests added/updated for the change).
- `npm ci`, `npm run typecheck`, and `npm run build` green for frontend changes.
- No secrets/credentials/PKI committed; secrets come from env/secret store.
- Generated code (jOOQ/proto) stays under `build/` — never committed.
- Public behavior changes reflected in OpenAPI and, if needed, the specs.
- **All AI-review comments resolved** (fixed or answered with a rationale) — see
  "AI review loop". A task is not done while review threads are still open.

## Task tracking
- `backend-specs/TASKS.md` / `frontend/docs/UI_TASKS.md` are the task
  **catalogs** (IS-/UI-IDs). **Live `In Progress` / `In review` status lives in
  GitHub Issues/Project** (org Project #1) keyed by ID — don't track those by
  editing catalog checkboxes. Move the board `Status` in lockstep with the work;
  these transitions are mandatory:
  - **In Progress** — the moment you **start** the task (before/at the first
    commit), so concurrent contributors see it is taken.
  - **In review** — as soon as you **open the PR**. The open PR *is* the trigger;
    do **not** wait for the AI reviewer's verdict to move it here.
  - **Done** — only **after the PR is merged** (then close the issue).
- **Marking a task Done:** flip its catalog checkbox to `[x]` ✅ **in the same PR
  that implements it** — this keeps the catalog current without a separate
  catalog-only PR, which branch protection on `master` would otherwise require.
  Move the **board `Status` to Done and close the issue only after that PR is
  merged**. Each PR edits only its own task's line, so conflicts are rare; only
  the aggregate snapshot count line is periodically retallied.
- File tasks with the **Task** issue form; labels are defined in
  `.github/labels.yml`. The Project board (live status by task ID) is an admin
  one-time setup: `gh project create` (needs the `project` token scope) or create
  it in the GitHub UI with a `Task ID` field, an `Area` (BE/FE/SDLC) field, and a
  status column.

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
