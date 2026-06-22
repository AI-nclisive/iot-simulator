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
- Later stages should extend the same UX model rather than introduce a new one.
- Tasks inside a stage may run in parallel only when `UI_TASKS.md` says they can.

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

- workspace shell;
- project workspace;
- source creation wizard;
- scan flow;
- recording flow;
- replay flow;
- source detail;
- evidence;
- baseline visual, accessibility, and edge-state review.

Exit gate:

- the user can move end to end through
  `Project -> Data Source -> Scan -> Record -> Replay -> Observe -> Evidence`
  without relying on shared-team features.

Implementation entry:

- start with [UI_TASKS.md](UI_TASKS.md), section `P0 - Core Workspace And Primary Flow`.

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

- continue with [UI_TASKS.md](UI_TASKS.md), section `P1 - Shared Usage, Reuse, And Operational Breadth`.

## P2

Goal:

- add advanced collaboration, richer repeatable flows, and future identity
  growth paths.

Focus:

- activity history;
- identity-provider compatibility;
- scenarios workspace;
- scenario builder;
- scenario run visibility;
- fault configuration.

Exit gate:

- a team can build richer test flows and grow the shared environment without a
  redesign of the UI model.

Implementation entry:

- continue with [UI_TASKS.md](UI_TASKS.md), section `P2 - Advanced Shared Workflows`.

## Delivery Hand-Off

Use the documents in this order:

1. `UI_PLAN.md`:
   decide which stage and wave is active.
2. `UI_TASKS.md`:
   choose the exact task and confirm whether it can run in parallel.
3. `UI_SCREEN_SPECS.md`:
   read the surface requirements for that task.
4. `DESIGN.md`:
   confirm the broader UX rules the implementation must preserve.

## Documentation Status

The UI documentation set is ready for development when used together:

- `DESIGN.md` for product UX rules;
- `UI_SCREEN_SPECS.md` for concrete surfaces;
- `UI_TASKS.md` for execution order and ownership;
- `UI_PLAN.md` for stage-level sequencing.
