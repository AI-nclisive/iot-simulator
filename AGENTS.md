# Agent Rules

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
