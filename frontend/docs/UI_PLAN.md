# UI Delivery Plan

## Purpose

`UI_PLAN.md` is the delivery-layer document for the Web UI.

Use it to understand:

- what gets built first;
- how the UI is split into delivery stages;
- where one stage ends and the next begins;
- which document to open next for implementation detail.

## Planning Principles

- The product is one platform delivered in stages.
- `Scan real source -> Record -> Replay` is the first delivery anchor.
- Shared-team features must not distort or delay the first usable local flow.
- P0 may include compatibility for later shared permissions and authentication,
  but it must not introduce visible login, mode switching, or shared-first
  shell chrome into the local-first core surfaces.
- Later stages should extend the same UX model rather than introduce a new one.
- Tasks inside a stage may run in parallel only when their board items say they
  can (org Project #1, `Area = FE`).

## Stage Summary

| Stage | Main outcome | Primary user result |
| --- | --- | --- |
| `P0` | First usable core workflow | A user can create or open a project, scan a real source, record behavior, replay it, observe runtime, and export evidence |
| `P1` | Shared-team usability and reuse breadth | A team can log in, reuse artifacts, edit more safely, and operate the simulator collaboratively |
| `P2` | Advanced collaboration and richer test flows | A team can build scenarios, inspect deeper history, and grow into expanded identity and role models |

## P0

Goal:

- deliver the first complete product loop around the main differentiating flow.

Focus:

- app shell;
- project surfaces;
- source creation wizard;
- scan flow;
- recording flow;
- replay flow;
- source detail;
- evidence;
- compatibility hooks that let P1 add shared login and permissions without a
  shell redesign;
- baseline visual, accessibility, and edge-state review.

Exit gate:

- the user can move end to end through
  `Project -> Data Source -> Scan -> Record -> Replay -> Observe -> Evidence`
  without relying on shared-team features;
- the same shell can later accept shared login and permission enforcement
  without changing its main project structure.

Implementation entry:

- pick a `UI-XXX` task from the board in stage `P0` (core shell and primary flow),
  then create its change with `/opsx:propose ui-XXX-<slug>`.

## P1

Goal:

- make the product operationally usable for shared teams and broader reuse.

Focus:

- shared login;
- role-aware shared behavior;
- edit-safety patterns;
- project lifecycle actions;
- recordings and samples reuse;
- full schema editing;
- deterministic settings;
- settings, admin, retention, and notifications;
- operational breadth such as clients, events, and automated-run visibility.

Exit gate:

- a shared team can use the simulator without confusion around permissions,
  authorship, reuse, or shared edits.

Implementation entry:

- continue with the board's `P1` tasks (shared usage, reuse, and operational
  breadth).

## P2

Goal:

- add advanced collaboration, richer repeatable flows, and future identity
  growth paths.

Focus:

- activity history;
- identity-provider compatibility;
- scenarios;
- scenario builder;
- scenario run visibility;
- fault configuration.

Exit gate:

- a team can build richer test flows and grow the shared environment without a
  redesign of the UI model.

Implementation entry:

- continue with the board's `P2` tasks (advanced shared workflows).

## Delivery Hand-Off

Use the documents in this order:

1. `UI_PLAN.md`:
   decide which stage and wave is active.
2. an `openspec/changes/ui-*` change (or the org board):
   choose the exact task and confirm whether it can run in parallel.
3. `openspec/specs/frontend-screens/spec.md`:
   read the surface requirements for that task.
4. `openspec/specs/frontend-shell/spec.md`:
   confirm the broader UX rules the implementation must preserve.

## Documentation Status

The UI documentation set is ready for development when used together:

- `openspec/specs/frontend-shell/spec.md` for product UX rules;
- `openspec/specs/frontend-screens/spec.md` for concrete surfaces;
- the org board / `openspec/changes/` for execution order and ownership;
- `UI_PLAN.md` for stage-level sequencing.
