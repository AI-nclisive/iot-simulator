# Agent Rules

## Project Documents

Read the doc that owns a topic before acting on it. Keep each fact in one place —
don't restate content that lives in another file.

- `SPEC.md` — source of truth for product capabilities (what users can and can't
  do). See "Working with SPEC.md".
- `ARCHITECTURE.md` — system map and binding architectural constraints.
- `STACK.md` — approved technology stack.
- `frontend/docs/DESIGN.md` — Web UI structure, UX flow, and interaction behavior.
- `frontend/docs/UI_PLAN.md`, `UI_SCREEN_SPECS.md`, `UI_TASKS.md` — UI delivery
  plan, per-surface specs, and task breakdown.
- `backend-specs/` — backend implementation specs (`00`–`08`) and the task
  register (`TASKS.md`); read the owning spec before implementing a task.
- `MEMORY.md` — project glossary and durable notes.

`ARCHITECTURE.md`, `STACK.md`, and `backend-specs/00`–`08` are governance: change
them only with explicit user approval, and propose the change first. No new
dependency without approval.

## Working with MEMORY.md

Read it before working. Write to it only when the user asks to remember something,
and only as a short durable note — never temporary task state.

## Working with SPEC.md

Source of truth for core product capabilities, grouped by epic (short name,
explanation, status). Don't add implementation details, edge cases, acceptance
criteria, or task breakdowns. Read it before recommending changes; explain any
change and get confirmation before editing — never change it silently.

## Contributions and task tracking

Follow `CONTRIBUTING.md` — it owns the full workflow (branching, PRs, the AI review
loop, task tracking, Definition of Done). Agent-specific points:

- **Before taking a task, verify it is free**, in this order: board `Status` is
  `Todo`; no open or merged PR references the ID (`gh pr list --search "IS-XXX"
  --state all`); the issue is open and unclaimed. The catalog checkbox alone is not
  proof — it can lag the board, and **on conflict the board wins**.
- **Claim it first:** flip the board `Status` to **In Progress** before writing any
  code. It is the opening action, never a backfill — a late flip leaves the board on
  `Todo` and misleads others into taking a claimed task.
- One task per branch and PR: `IS-XXX` (backend/SDLC) or `UI-XXX` (frontend).
- Run `./gradlew build` and confirm it is green before reporting work done or
  opening a PR; report the real result. Add or update tests for every change.
