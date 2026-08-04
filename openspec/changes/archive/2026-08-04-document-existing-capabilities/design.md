## Context

See proposal.md - Why. The 8 backend-specs files, `SPEC.md`, and the two
frontend design docs were read in full against the current code (Java
sources, Flyway migrations, `@RestController` classes, `frontend/src`) before
writing each delta spec, so this baseline reflects the system as it is today,
not the drafts.

## Goals / Non-Goals

- Goal: one openspec capability per backend-specs file (plus a merged pair
  for the frontend), each accurate against code.
- Goal: surface every material doc/code discrepancy found during conversion
  as a "Known gaps" note in the relevant spec, rather than silently fixing or
  silently dropping it.
- Non-goal: fixing any of those gaps (e.g. widening the `schema_nodes.kind`
  check constraint, adding the missing `Activity` nav link, adding
  `SchemaNodeMsg` proto fields). Those are behavior changes and belong in
  their own future openspec changes.
- Non-goal: converting `docs/FRONTEND_BACKEND_CONTRACT_MAP.md` requirement-by-
  requirement - it is a generated-style controller/endpoint index that
  duplicates `api-contract`; it is deleted rather than converted.

## Decisions

- **One capability per backend-specs file** (`protocol-model`,
  `worker-contract`, `domain-model`, `db-schema`, `api-contract`,
  `artifact-formats`, `module-structure`, `auth-modes`) rather than fewer,
  larger capabilities - keeps each spec's scope matching what a future change
  will actually touch (e.g. a DB-only change only deltas `db-schema`).
- **Two frontend capabilities** (`frontend-shell`, `frontend-screens`)
  instead of one per old doc file - `DESIGN.md`'s structural/interaction
  rules and `UI_SCREEN_SPECS.md`'s page inventory are different kinds of
  contract (behavior rules vs. surface existence) and change independently.
  `QA_CHECKLIST.md` and `UI_PLAN.md` are process documents (a manual test
  checklist and a staging plan), not behavior contracts - they are left as
  plain docs under `frontend/docs/` rather than forced into spec format.
- **`SPEC.md`'s epics are not a separate capability.** A spec describes
  current true behavior; `SPEC.md`'s "ToDo" framing is backlog, which
  belongs in `openspec/changes/`, not `openspec/specs/`. Implemented epics'
  content is folded into the relevant capability above; unimplemented epics
  become change-proposal stubs in the "Retire TASKS.md/UI_TASKS.md" follow-up.
- **Known gaps stay as prose notes under each spec, not as scenarios.** A gap
  is current *non*-behavior (something that doesn't work yet, e.g. the
  `DATA_TYPE` kind/constraint mismatch found while writing `db-schema`); a
  SHALL/scenario pair would misstate it as a guarantee.

## Risks / Trade-offs

- [Risk] Writing specs from doc + a point-in-time code read can still drift
  quickly (the same problem that made the old docs stale) → Mitigation: from
  now on, every future change updates its capability's delta spec as part of
  the change itself (openspec's own apply→archive flow), which is the whole
  reason for this migration.
- [Risk] Splitting into 10 capabilities instead of ~5 top-level docs makes
  the map slightly less skimmable → Mitigation: `openspec list --specs` /
  `openspec view` give the index; capability names are chosen to mirror the
  existing module/screen names contributors already know.

## Migration Plan

1. This change's spec deltas are synced into `openspec/specs/` (`openspec
   change archive document-existing-capabilities`).
2. `backend-specs/{01..08}_*.md`, `SPEC.md`,
   `frontend/docs/{DESIGN,UI_SCREEN_SPECS}.md`, and
   `docs/FRONTEND_BACKEND_CONTRACT_MAP.md` are deleted in the same PR (no
   dangling stale copies).
3. Follow-up changes (tracked as separate tasks, not part of this change)
   handle: retiring `TASKS.md`/`UI_TASKS.md`, rewriting `.claude/skills/*`,
   and the CI `catalog-sync` gate.

No rollback beyond `git revert` is needed - this is a documentation-only
change with no runtime effect.
