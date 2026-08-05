## Context

See proposal.md — Why. The trigger is a tool behavior worth writing down:
`openspec archive` merges only parsed `### Requirement:` blocks into the main
spec. Any other prose in a delta (`## Known gaps`, notes, tables) is dropped
without a warning. `## Purpose` is the one exception — archive copies it into a
newly created main spec.

## Goals / Non-Goals

- Goal: every limitation the baseline knew about is expressed so that it
  survives archive, and the live specs stop asserting things the code does not
  do.
- Non-goal: fixing the limitations themselves. Widening the
  `schema_nodes.kind` constraint, adding the extended fields to the worker
  schema message, adding the `Activity` nav link, and building a settings API
  are each their own future change with their own board task.
- Non-goal: extending the baseline's coverage to every shipped endpoint. The
  api-contract spec covers the main resource groups; several real endpoint
  groups (samples, synthetic runs, rescan, project overview, meta, admin
  activity) still have no requirement. That is a known coverage gap, tracked
  separately — not silently closed here by inventing requirements.

## Decisions

- **Limitations become normative requirement text, not footnotes.** A
  limitation is current behavior ("X is rejected", "Y is Actuator-only"), so it
  belongs in a `SHALL` with scenarios. This is what makes it survive archive,
  and it reads honestly: the spec says what the system does, and the desired
  end state lives in a future change.
- **State limitations positively where possible.** "Liveness comes from
  Actuator" beats "there is no /healthz" — the former is testable, the latter
  is an absence that no scenario can assert.
- **Keep the every-route-has-a-nav-entry rule, but name its one exception.**
  Deleting the rule would lose a real design constraint; leaving it absolute
  would keep the spec wrong. Naming `/activity` as the known exception keeps
  both the rule and the truth.
- **Correct rather than delete the two wrong requirements.** `MODIFIED` with
  the full block copied (archive replaces the whole requirement), so the
  surrounding scenarios are preserved.

## Risks / Trade-offs

- [Risk] A limitation written as a `SHALL` can read as a design intent someone
  later defends instead of fixing → Mitigation: each one says it is a current
  limitation and points at the capability that owns the eventual fix.
- [Risk] The same prose-dropping mistake recurs in a future change →
  Mitigation: `AGENTS.md` → "Working with openspec" now states that only
  requirement blocks survive archive.

## Migration Plan

Archive this change (`openspec archive fix-baseline-spec-accuracy`), which
merges the deltas into `openspec/specs/`. Verify afterwards that the intended
text is actually present in the live specs — the failure this change fixes was
exactly a silent drop at this step, so re-reading the live files is part of
the work, not optional.
