# Agent Rules

## Project Documents

Read the document that owns a topic before acting on it, and keep each fact in
one place — do not restate content that lives in another file.

- `SPEC.md` — source of truth for product capabilities (what users can and cannot
  do). See "Working With SPEC.md".
- `ARCHITECTURE.md` — high-level system map and binding architectural constraints.
  Do not change without explicit user approval; propose changes first.
- `STACK.md` — approved technology stack. No new dependency without explicit approval.
- `DESIGN.md` — product Web UI design: screens, flows, and interaction patterns.
- `UI_PLAN.md` / `UI_SCREEN_SPECS.md` / `UI_TASKS.md` — UI design plan, per-screen
  specifications, and task breakdown.
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
