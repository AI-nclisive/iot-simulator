## Context

UI-512's implementation is already in `master`; only its delivery record was
left in the retired `UI_TASKS.md` catalog.

## Goals / Non-Goals

**Goals:**

- Preserve a traceable OpenSpec archive for UI-512.

**Non-Goals:**

- Change Manual Schema behaviour or recreate the retired catalog.

## Decisions

- Use `skip_specs: true` because this is a documentation-record migration, not
  a new behaviour change.

## Risks / Trade-offs

- The archive does not repeat the implementation details; the merged UI code
  remains the historical implementation record.
