# Agent Rules

## Project Documents

Read the doc that owns a topic before acting on it. Keep each fact in one place —
don't restate content that lives in another file.

- `openspec/specs/<capability>/spec.md` — source of truth for what the system
  does today (backend: `protocol-model`, `worker-contract`, `domain-model`,
  `db-schema`, `api-contract`, `artifact-formats`, `module-structure`,
  `auth-modes`; frontend: `frontend-shell`, `frontend-screens`). Read the owning
  capability spec before implementing a task.
- `openspec/changes/<id>-<slug>/` — an in-flight task's proposal + design +
  tasks + spec delta; `openspec/changes/archive/` holds completed ones. See
  "Working with openspec".
- `ARCHITECTURE.md` — system map and binding architectural constraints.
- `STACK.md` — approved technology stack.
- `frontend/docs/UI_PLAN.md` — UI delivery stage/wave sequencing (process, not
  behavior — behavior lives in `openspec/specs/frontend-*`).
- `MEMORY.md` — project glossary and durable notes.

`ARCHITECTURE.md`, `STACK.md`, and `openspec/specs/` are governance: change
them only with explicit user approval, and propose the change first (a spec
change always goes through an `openspec/changes/` proposal, never a direct
edit). No new dependency without approval.

## Working with MEMORY.md

Read it before working. Write to it only when the user asks to remember something,
and only as a short durable note — never temporary task state.

## Working with openspec

`openspec/specs/` describes current true behavior, capability by capability —
not a backlog and not implementation detail. Before implementing a task, run
`/opsx:propose` to create or continue its `openspec/changes/<id>-<slug>/`
change (proposal + spec delta + design + tasks), per `[[start-task]]`. Read the
target capability's current spec before writing the delta — don't restate
unrelated capabilities, and don't invent a requirement just to pad the change.
Archive the change in the same PR that implements it (`/open-pr` does this) —
that's what merges the delta into `openspec/specs/`. Never hand-edit
`openspec/specs/*.md` directly; it's always the output of an archived change.

## Contributions and task tracking

Follow `CONTRIBUTING.md` — it owns the full workflow (branching, PRs, the AI review
loop, task tracking, Definition of Done). Agent-specific points:

- **Before taking a task, verify it is free**, in this order: board `Status` is
  `Todo`; no open or merged PR references the ID (`gh pr list --search "IS-XXX"
  --state all`); the issue is open and unclaimed. An existing, non-archived
  `openspec/changes/` folder alone is not proof — it can lag the board, and
  **on conflict the board wins**.
- **Claim it first:** flip the board `Status` to **In Progress** before writing any
  code. It is the opening action, never a backfill — a late flip leaves the board on
  `Todo` and misleads others into taking a claimed task.
- One task per branch and PR: `IS-XXX` (backend/SDLC) or `UI-XXX` (frontend).
- Run `./gradlew build` and confirm it is green before reporting work done or
  opening a PR; report the real result. Add or update tests for every change.
