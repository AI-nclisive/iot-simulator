# Contributing

This repo is built in parallel by many people and agents via feature branches and
pull requests. These rules keep that smooth.

## Prerequisites
- JDK 25 (toolchain). Gradle runs via the committed wrapper (`./gradlew`).
- Node.js 20 for the frontend (`nvm use`; version recorded in `.nvmrc`).
- Docker for Testcontainers integration tests — they skip locally without it; CI
  always runs them.

## Build & test
```bash
./gradlew build        # compile + all tests (unit + ITs + ArchUnit)
npm ci                 # install locked frontend dependencies
npm run typecheck      # frontend TypeScript check
npm run build          # production frontend build
```
Run these and confirm green before opening a PR.

## Claude Code skills

This workflow is encoded as **Claude Code project skills** under `.claude/skills/`
so every contributor/agent runs it the same way. The skills hold the executable
steps (and `gh` commands); the rules below remain the source of truth. Invoke a
skill by typing its slash command.

| Skill | Use it to |
| --- | --- |
| `/start-task IS-XXX` | Claim a task before coding: verify free → board **In Progress** → linked branch. |
| `/open-pr` | Run DoD checks, archive the task's openspec change in-PR, open the PR, arm auto-merge, board **In review**. |
| `/review-loop` | Work the Claude PR review to completion (fix/rebut → push → repeat). |
| `/new-worker <proto>` | Scaffold a new out-of-process protocol worker (contract impl; no supervisor change). |
| `/flyway-migration <name>` | Add a collision-safe, append-only DB migration. |
| `/board-sync` | Reconcile org Project #1 with openspec's change/spec state. |
| `/opsx:propose <name>` | Create/continue a task's openspec change (proposal + spec delta + design + tasks). |

Recommended **built-in** skills, by stage (run locally to cut review rounds):
- Before `/open-pr`: **`/code-review`** (correctness + cleanup) and
  **`/security-review`** (no secrets/PKI, authz, exportable artifacts) — the CI
  Claude reviewer runs anyway, but local passes shorten the loop.
- For behavior-affecting changes: **`/verify`** or **`/run`** to confirm it works,
  not just compiles.
- Environment: **`/fewer-permission-prompts`** to trim repeated `gh`/Gradle/npm
  prompts; **`update-config`** to wire any team-agreed hooks into `settings.json`.

## Task IDs
- Backend / repo / process: **`IS-XXX [AREA] short name`** (`[AREA]` is `[BE]` or
  `[SDLC]`). Frontend: **`UI-XXX [FE] short name`**. File a **Task** issue to mint
  a new ID (there is no catalog file to append to anymore); browse existing IDs
  on the board or under `openspec/specs/`/`openspec/changes/`.
- Reuse the ID everywhere: branch `feat/IS-038-...`, issue title `IS-038 short name`
  (area via the form's **Area** field), PR `Implements: IS-038`, openspec change
  folder `openspec/changes/is-038-short-slug/`.

## Branching
- Branch off `master`; one task per branch/PR. Name: `feat/IS-123-short-slug`
  (also `fix/`, `docs/`, `chore/`, `test/`).
- **Link the branch to its task issue** when you start, so in-flight work is
  traceable on the board before a PR exists:
  ```bash
  gh issue develop <issue#> --name feat/IS-123-short-slug --base master
  git branch --set-upstream-to=origin/feat/IS-123-short-slug
  ```
  Verify with `gh issue develop --list <issue#>`. Do this alongside moving the board
  to **In Progress** (see "Task tracking").

## Commits & PRs
- **Write all GitHub text in English** — commits, PR titles/descriptions, issues,
  and every review reply.
- Conventional Commits: `type(scope): subject` (e.g. `feat(schema): ...`).
- Reference the task: `Implements: IS-123`, `Closes: #<issue>`. Keep PRs small and
  focused; fill in the template checklist.
- **Squash merge only**, linear history; green CI plus one approving review required.
- **Arm auto-merge when you open the PR:** `gh pr merge <n> --auto --squash`. It then
  merges itself once the Claude reviewer's APPROVE lands and `build` is green — no
  manual merge. (Auto-merge is enabled on the repo; see `.github/OWNER_SETUP.md`.)

## AI review loop
Every PR is reviewed by a Claude reviewer (IS-112; `.github/workflows/claude-review.yml`).
It posts inline comments (`[blocking]` / `[nit]`) and one verdict comment, then submits
a formal GitHub review: **APPROVE** only when nothing blocks and every thread is
resolved, otherwise **REQUEST_CHANGES**. This gates merge (required status check: `build`).

By the time it runs, the task is already **In review** (moved when the PR opened).
Work the review to completion:
1. Wait for the verdict and inline comments.
2. For each finding, either **fix it and reply** saying what you changed, or **reply
   with a rationale** for leaving it. Then **push** — each push re-triggers the review
   (a reply alone does not).
3. Repeat until the verdict is `✅ Mergeable` with no unresolved comments (the APPROVE
   then lands and the PR auto-merges), or **3 rounds** have completed (then summarize
   any still-open points in the PR description for a human reviewer).

**Resolving threads is the reviewer's prerogative, never the author's** — you only
respond; the reviewer resolves the threads it is satisfied with and never approves
while any thread is open. A feature is done only when every comment has been responded
to and `./gradlew build` is green.

## Definition of Done
- `./gradlew build` green (tests added/updated for the change).
- Frontend changes: `npm ci`, `npm run typecheck`, `npm run build` green.
- No secrets/credentials/PKI committed; secrets come from env/secret store.
- Generated code (jOOQ/proto) stays under `build/` — never committed.
- Public behavior changes reflected in OpenAPI and in the task's openspec spec
  delta (merged into `openspec/specs/` on archive).
- Every AI-review comment responded to (see "AI review loop").

## Task tracking
`openspec/specs/` (what exists) and `openspec/changes/` / `openspec/changes/archive/`
(what's in flight / done) are the task **record**; org **Project #1** is their live
mirror, **one issue per ID**. When you add, rename, remove, or re-scope a task,
update **both** the openspec change and the board (never duplicate an ID).

Live `In Progress` / `In review` status lives on the **board only** — an
un-archived change folder existing doesn't by itself mean "still in progress"
(it could be stale). Move status in lockstep with the work:
- **In Progress** — set **first, before any code**: verify the task is free → flip
  `Status` → create the linked branch → propose the openspec change → then
  implement. Never backfill it after coding, or the board still shows `Todo` and
  misleads other contributors.
- **In review** — as soon as you **open the PR** (don't wait for the reviewer's verdict).
- **Done** — archive the task's openspec change **in the implementing PR** (this
  avoids an archive-only PR that branch protection on `master` would otherwise
  force; it also merges the change's spec delta into `openspec/specs/`), then
  move the board to **Done** and close the issue **after the PR merges**.

Each PR archives only its own task's change, so conflicts are rare. CI enforces the
pairing: a PR whose body has `Implements: IS-/UI-…` must add an
`openspec/changes/archive/<date>-<id>-<slug>/` in the same PR
(`.github/workflows/ci.yml` → `catalog-sync`), so a merged task never leaves
`openspec/specs/` undocumented. (`Closes: #…` is not the trigger — it can
reference non-task issues such as bug reports; only `Implements: IS-/UI-…` arms
catalog-sync.) **On conflict the board wins** — a task with a merged PR but no
archived change is still done; archive it in your next related PR, don't
re-implement.

File tasks with the **Task** issue form (labels in `.github/labels.yml`); board fields
are `Status`, `Task ID`, and `Area` (BE/FE/SDLC).

## Parallel-work conventions
- **Flyway migrations** are append-only; never reuse a version number. If a `Vn` may
  collide, use a timestamped version (`V20260623_1530__name.sql`).
- Prefer changes scoped to one Gradle module to reduce conflicts.
- Add/bump dependencies only in `gradle/libs.versions.toml`.

## Governance
`ARCHITECTURE.md`, `STACK.md`, and `openspec/specs/` change only with prior
owner approval — propose first (see `AGENTS.md`). No new dependency without approval.

## Branch protection (repo admin)
Configuration record: [`.github/OWNER_SETUP.md`](.github/OWNER_SETUP.md). Requires
repo **admin**. `master` is protected (status check `build` = the CI job name):
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
Also in repo Settings: allow **squash merging only** and "Automatically delete head
branches". (Set `enforce_admins: true` later once more than one approver is available.)
