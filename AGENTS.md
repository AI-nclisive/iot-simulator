# Agent Rules

## Project Documents

Read the document that owns a topic before acting on it, and keep each fact in
one place — do not restate content that lives in another file.

- `SPEC.md` — source of truth for product capabilities (what users can and cannot
  do). See "Working With SPEC.md".
- `ARCHITECTURE.md` — high-level system map and binding architectural constraints.
  Do not change without explicit user approval; propose changes first.
- `STACK.md` — approved technology stack. No new dependency without explicit approval.
- `frontend/docs/DESIGN.md` — product Web UI structure, UX flow, and interaction
  behavior.
- `frontend/docs/UI_PLAN.md` / `frontend/docs/UI_SCREEN_SPECS.md` /
  `frontend/docs/UI_TASKS.md` — UI delivery plan, per-surface specifications,
  and task breakdown.
- `MEMORY.md` — project glossary and durable notes. See "Working With MEMORY.md".

## Working With MEMORY.md

Always read `MEMORY.md` before working in this project.

Do not write to `MEMORY.md` unless the user explicitly asks to remember something.

When the user asks to remember something, add a short, durable note. Do not use `MEMORY.md` for temporary task state.

## Working With SPEC.md

`SPEC.md` is the source of truth for the product's main capabilities.

Keep `SPEC.md` focused on core capabilities only: what users can do and what users cannot do.

Do not expand `SPEC.md` with implementation details, edge cases, micro-requirements, acceptance criteria, or task breakdowns unless the user explicitly asks to change its structure.

Group capabilities by epics. Each capability should have a short name, a clear explanation, and an implementation status.

When a user proposes adding, changing, or removing a capability, read `SPEC.md` before making recommendations.

Explain the proposed change clearly and ask for user confirmation before editing `SPEC.md`.

Do not change `SPEC.md` silently.

Do not add technical details to `SPEC.md` unless the user explicitly confirms that the detail is a product capability.

## Working With backend-specs/

`backend-specs/` holds the backend implementation specs (`00`–`08`) and the task
register (`TASKS.md`). Read the owning spec before implementing a task. Treat
`00`–`08` as governance: change them only with explicit user approval (propose
first), same as `ARCHITECTURE.md` / `STACK.md`.

## Task Tracking

The task catalogs and the GitHub **Project** are kept in sync — the Project (org
Project #1, "IoT Simulator":
<https://github.com/orgs/AI-nclisive/projects/1>) is the live mirror of the
catalogs, with **one issue per task ID**. Catalogs:

- `backend-specs/TASKS.md` — `IS-*` (`[BE]` / `[SDLC]`).
- `frontend/docs/UI_TASKS.md` — `UI-*` (`[FE]`).

When you change tasks, change **both** the file and the Project:

- Adding, renaming, removing, or re-scoping a task in a catalog → also create,
  edit, or close the matching issue and board item (by `IS-`/`UI-` ID); never
  create a duplicate ID.
- In-progress status (`In Progress` / `In review`) lives on the **board only**
  (`Status` field + open issue) — don't edit catalog checkboxes for those. Move it
  in step with the work: **In Progress before you write any code** — it is the first
  action of the task (verify the task is free → flip `Status` to **In Progress** →
  create the linked branch → only then implement), **never** set postfactum after the
  code is written, because a late flip leaves the board showing `Todo` and misleads
  contributors into picking up a task that is already taken. **In review** as soon as
  you **open the PR** (the open PR is the trigger — don't wait for the AI reviewer's
  verdict).
- **Done is recorded in two places:** flip the catalog checkbox to `[x]` ✅ **in
  the implementation PR itself** (the same PR that delivers the work — this avoids
  a separate catalog-only PR, which the no-direct-push-to-`master` rule would
  otherwise force), and move the board `Status` to **Done** + close the issue
  **only after that PR is merged**. Each PR edits only its own task's line, so
  catalog conflicts are rare. CI enforces this pairing: a PR whose body links a
  task (`Implements: IS-/UI-…`) must also edit that task's catalog
  line in the same PR, so a merged task can never leave a stale `[ ]` behind (see
  `.github/workflows/ci.yml` → `catalog-sync` job). (`Closes: #…` is not used as
  the trigger — it can reference non-task issues such as bug reports.)
- **Source of truth on conflict: the board wins.** The Project board is the live
  mirror; the catalogs can lag. A task whose board `Status` is `Done` (or that has
  a merged PR) but whose catalog checkbox is still `[ ]` is **done** — fix the
  stale checkbox in your next related PR rather than re-implementing the task.
- Issue shape (see `.github/ISSUE_TEMPLATE/task.yml`): title `IS-XXX [AREA] name`
  for backend/SDLC or `UI-XXX [FE] name` for frontend, label `type:task`
  (+ `priority:P*`); board fields `Status` / `Task ID` / `Area` (`Area` ∈
  BE / FE / SDLC).

## Code Contributions

Follow `CONTRIBUTING.md`. For agents specifically:

- **Before taking a task, verify it is actually open — in this order:**
  1. Board `Status` is `Todo` (not `In Progress` / `In review` / `Done`) — the
     board is the live source of truth.
  2. No open or merged PR references the task ID (e.g.
     `gh pr list --search "IS-XXX" --state all`).
  3. The issue is open with no comment claiming it.

  If any check fails, pick a different task. **The catalog checkbox alone is not
  sufficient evidence a task is free** — it can lag behind the board (a merged PR
  may have moved the board to `Done` without flipping the checkbox). When the
  catalog and the board disagree, the board wins (see "Task Tracking").
- One task per branch and PR: use an `IS-XXX` from `backend-specs/TASKS.md` for
  backend/SDLC work, or a `UI-XXX` from `frontend/docs/UI_TASKS.md` for frontend
  work.
- Before reporting work done or opening a PR, run `./gradlew build` and confirm it
  is green; report the real result. Never claim done without building and testing.
- Branch `feat/IS-xxx-...` or `feat/UI-xxx-...`; Conventional Commit titles;
  squash merge into `master`. **Link the branch to its task issue on the board**
  when you create it — `gh issue develop <issue#> --name feat/IS-xxx-... --base master`
  (see `CONTRIBUTING.md` → "Branching"), so the board shows the branch against the task.
- When you open the PR, arm auto-merge: `gh pr merge <n> --auto --squash`. The PR then
  merges itself once the Claude reviewer's APPROVE lands and `build` is green — no
  manual merge step (see `CONTRIBUTING.md`).
- Add or update tests for every change.
- Move the board `Status` in step with the work — **In Progress before you write any
  code** (the first action of the task, not a backfill after implementing; a late flip
  misleads contributors into taking an already-claimed task), **In review** when you
  open the PR (see "Task Tracking" above for the full transitions).
- After opening a PR, follow the **AI review loop** in `CONTRIBUTING.md`: wait for the
  Claude reviewer's verdict (it submits a formal APPROVE / REQUEST_CHANGES that gates
  merge), then for each finding either fix it and reply saying what you changed, or
  reply with a rationale for why the comment is incorrect or not applicable; then push
  and wait for re-review. **Never resolve review conversations yourself — that is the
  reviewer's prerogative;** you only respond, and the reviewer resolves the threads it
  is satisfied with. **The task is finished only once every reviewer comment has been
  responded to** (or after 3 review rounds — see the full loop in `CONTRIBUTING.md`)
  and the build is green — don't treat the work as done while comments are still
  unanswered.
- Keep tasks in sync with the GitHub Project (see "Task Tracking"): the board
  `Status` is the live source for `In Progress` / `In review`; mark the catalog
  checkbox `[x]` ✅ in the implementation PR and flip the board to **Done** only
  after that PR is merged.
- No new dependency without approval; add versions only in
  `gradle/libs.versions.toml`.
- Keep generated code (jOOQ/proto) out of version control; never commit secrets.
