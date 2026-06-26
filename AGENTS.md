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
  in step with the work: **In Progress** the moment you start the task, **In review**
  as soon as you **open the PR** (the open PR is the trigger — don't wait for the AI
  reviewer's verdict).
- **Done is recorded in two places:** flip the catalog checkbox to `[x]` ✅ **in
  the implementation PR itself** (the same PR that delivers the work — this avoids
  a separate catalog-only PR, which the no-direct-push-to-`master` rule would
  otherwise force), and move the board `Status` to **Done** + close the issue
  **only after that PR is merged**. Each PR edits only its own task's line, so
  catalog conflicts are rare.
- Issue shape (see `.github/ISSUE_TEMPLATE/task.yml`): title `IS-XXX [AREA] name`
  for backend/SDLC or `UI-XXX [FE] name` for frontend, label `type:task`
  (+ `priority:P*`); board fields `Status` / `Task ID` / `Area` (`Area` ∈
  BE / FE / SDLC).

## Code Contributions

Follow `CONTRIBUTING.md`. For agents specifically:

- One task per branch and PR: use an `IS-XXX` from `backend-specs/TASKS.md` for
  backend/SDLC work, or a `UI-XXX` from `frontend/docs/UI_TASKS.md` for frontend
  work.
- Before reporting work done or opening a PR, run `./gradlew build` and confirm it
  is green; report the real result. Never claim done without building and testing.
- Branch `feat/IS-xxx-...` or `feat/UI-xxx-...`; Conventional Commit titles;
  squash merge into `master`.
- When you open the PR, arm auto-merge: `gh pr merge <n> --auto --squash`. The PR then
  merges itself once the Claude reviewer's APPROVE lands and `build` is green — no
  manual merge step (see `CONTRIBUTING.md`).
- Add or update tests for every change.
- Move the board `Status` in step with the work — **In Progress** when you start,
  **In review** when you open the PR (see "Task Tracking" above for the full transitions).
- After opening a PR, follow the **AI review loop** in `CONTRIBUTING.md`: wait for the
  Claude reviewer's verdict (it submits a formal APPROVE / REQUEST_CHANGES that gates
  merge), fix each finding (or reply why the current choice is best), mark the thread
  resolved, push, and wait for re-review. **The task is
  finished only once every reviewer comment is resolved** (or after 3 review rounds —
  see the full loop in `CONTRIBUTING.md`) and the build is green — don't treat the
  work as done while review threads are still open.
- Keep tasks in sync with the GitHub Project (see "Task Tracking"): the board
  `Status` is the live source for `In Progress` / `In review`; mark the catalog
  checkbox `[x]` ✅ in the implementation PR and flip the board to **Done** only
  after that PR is merged.
- No new dependency without approval; add versions only in
  `gradle/libs.versions.toml`.
- Keep generated code (jOOQ/proto) out of version control; never commit secrets.
