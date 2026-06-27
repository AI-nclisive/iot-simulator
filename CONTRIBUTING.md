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
- **Link the branch to its task issue on the board.** Create the branch with
  `gh issue develop <issue#> --name feat/IS-123-short-slug --base master` (this
  records the issue↔branch link, visible in org Project #1 against the task), then
  `git branch --set-upstream-to=origin/feat/IS-123-short-slug`. Do this when you
  start the task, alongside moving the board `Status` to **In Progress** — so
  in-flight work is traceable on the board before a PR exists. Verify with
  `gh issue develop --list <issue#>`.

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
matching that verdict: **APPROVE** only when nothing blocks **and every review thread
is resolved** (including nits and any human's comments), **REQUEST_CHANGES** otherwise.
This gates merge: the APPROVE supplies branch protection's required approving review,
and a REQUEST_CHANGES blocks merge until a later push earns an APPROVE. The required
status check stays `build`.

**Resolving review conversations is the reviewer's prerogative, never the PR author's.**
As the PR author (human or agent) you do **not** click "Resolve conversation". For each
comment you only **respond**: either fix the finding and reply saying what you changed,
or leave the code as-is and reply with a rationale for why the comment is incorrect or
not applicable. The reviewer then reads your replies and the new diff and resolves the
threads it is satisfied with — it resolves its own thread when the issue is fixed, or
when it agrees with your pushback; if it is not convinced it leaves the thread open. It
never approves while any thread is open, so to earn the approval you must **respond to
every comment and push** (a reply alone does not re-trigger the review) — you cannot,
and must not, shortcut it by resolving threads yourself.

By the time the review runs the task is already **In review** (moved there when the
PR was opened — see "Task tracking"). Working the review to completion is part of
finishing the task, not an optional follow-up. After opening (or updating) a PR:
1. **Wait for the verdict** and the inline comments to land.
2. For each finding, either **fix it and reply** on the comment saying what you
   changed, or **reply with a rationale** for why the comment is incorrect or not
   applicable and leave the code as-is. **Do not resolve the thread yourself** — that
   is the reviewer's call. Then **push to the same branch** — each push re-triggers
   the review.
3. **Wait for the new verdict** and repeat until either:
   - the verdict is `✅ Mergeable` with **no unresolved comments** (the reviewer has
     resolved the threads it was satisfied with) — the reviewer's **APPROVE** then
     lands and, with auto-merge armed and `build` green, the PR squash-merges itself, or
   - **3 review rounds** have completed (then summarize any still-open points for the
     human reviewer in the PR description, leaving each thread answered with a fix or a
     rationale for the reviewer or a human to resolve).

**A feature is finished only when every reviewer comment has been responded to** —
each one fixed-and-acknowledged or answered with a rationale — and `./gradlew build`
is green. Resolving the threads themselves is the reviewer's step, not yours; do not
consider the work done, or walk away from the PR, while any comment is still
unanswered. The board `Status` moves to **Done** only when the PR is merged; the
catalog checkbox itself is flipped inside the implementation PR (see "Task tracking").

## Definition of Done
- `./gradlew build` green (tests added/updated for the change).
- `npm ci`, `npm run typecheck`, and `npm run build` green for frontend changes.
- No secrets/credentials/PKI committed; secrets come from env/secret store.
- Generated code (jOOQ/proto) stays under `build/` — never committed.
- Public behavior changes reflected in OpenAPI and, if needed, the specs.
- **All AI-review comments responded to** (fixed-and-acknowledged or answered with a
  rationale) — see "AI review loop". Resolving the threads is the reviewer's
  prerogative; a task is not done while any comment is still unanswered.

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
