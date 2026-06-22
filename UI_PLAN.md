# UI Design Plan

This file is the readable planning layer for the IoT Data Source Simulator UI.

Use it to explain the UI direction to the team, understand delivery order, and see
how the design maps back to `SPEC.md`, `ARCHITECTURE.md`, and `STACK.md`.

Detailed files:

- `DESIGN.md` - product-level UI decisions and UX rules.
- `UI_SCREEN_SPECS.md` - screen-by-screen requirements.
- `UI_TASKS.md` - implementation-ready UI work breakdown.

## 1. Product Summary

The UI is a mode-aware operational workbench for building, running, observing, and
reusing simulated industrial data sources.

Core product story:

1. Scan real source.
2. Record real behavior.
3. Replay through a simulated source.
4. Observe runtime state.
5. Export evidence.

The product is delivered in stages. `P0`, `P1`, and `P2` describe delivery order,
not product ambition.

## 2. Delivery Stages

### P0 - First Usable Release Slice

Goal: make the core simulator workflow usable without waiting for the full shared
team feature set.

Included:

- trusted local mode without required login;
- app shell, navigation, runtime panel, shared UI states;
- project entry and project overview;
- OPC UA and Modbus TCP data-source creation;
- protocol-extensible Create Data Source wizard;
- primary Scan -> Record -> Replay flow;
- data-source list and detail;
- live values and runtime state;
- evidence list, detail, export, and export failure recovery;
- baseline accessibility, edge states, visual system, and role-aware patterns.

P0 success test:

- A user can create or open a project, create an OPC UA or Modbus data-source,
  scan a real source, record data, replay it, observe values, and export evidence.

### P1 - Production-Usable Breadth

Goal: make the product practical for shared teams and broader reuse.

Included:

- shared login/password flow;
- Admin/User shared-mode behavior;
- user and access management;
- edit-safety pattern;
- multiple data-sources;
- manual schema creation;
- synthetic data generation;
- deterministic replay/synthetic settings;
- project import/export and lifecycle actions;
- recordings/samples list, import, preview, export;
- connected clients, health, and runtime events;
- automated run visibility;
- retention/cleanup entry points;
- responsive/browser/platform baseline for Linux, Windows, and macOS.

P1 success test:

- A team can use the shared environment with login, Admin/User behavior, reusable
  data, project lifecycle actions, automated run visibility, and operational
  history without confusing local and shared modes.

### P2 - Advanced Shared Workflows

Goal: add deeper collaboration, richer test flows, and expanded access models.

Included:

- custom scenario builder;
- scenario run view;
- fault configuration;
- full activity/audit view;
- advanced shared editing workflows;
- identity provider and expanded-role compatibility;
- future viewer/operator/editor/admin role expansion.

P2 success test:

- A team can build and operate richer repeatable scenarios, inspect audit history,
  and grow beyond the current Admin/User role model without redesigning the UI.

## 3. Product Decisions

Current decisions:

- UI model: one Web UI for trusted local and shared team modes.
- P0 auth: trusted local mode can run without login.
- P1 auth: shared team mode introduces login/password.
- Current shared roles: Admin and User.
- Shared User permissions: observe everything and start data-sources only.
- Shared Admin permissions: create, edit, start, stop, delete, import, export,
  and administer.
- Project visibility: all signed-in shared users can see all projects.
- Main flow: Scan real source -> Record -> Replay.
- Initial protocols: OPC UA and Modbus TCP.
- Data-source creation: one wizard that can support future protocols.
- Schema editing: full editor for every editable part of the schema.
- Evidence capture: automatic for every run; export is explicit.
- Evidence export: Report, Full bundle, Value timeline CSV.
- Shared editing: automatic locks/read-only state, not manual checkout.
- Accessibility baseline: WCAG 2.2 AA.
- Frontend stack: React, TypeScript, Vite, React Router, TanStack Query/Table,
  Zustand, Radix UI, Tailwind.

## 4. Information Architecture

Primary areas:

- Project Entry - choose, create, or import a project.
- Project Overview - command center for project state.
- Data Sources - create, run, edit, and observe simulated sources.
- Recordings & Samples - manage reusable real or prepared data.
- Scenarios - build and run repeatable test flows.
- Evidence - review and export captured run evidence.
- Settings - project and environment settings.
- Admin - user and role management.
- Activity / Audit - team and automation history.

## 5. Main Workflows

### Scan Real Source -> Record -> Replay

Stage: P0

Why it matters:

- This is the product differentiator.
- It turns real industrial behavior into reusable simulator assets.

UI must make clear:

- real endpoint vs simulated endpoint;
- scan progress and partial scan warnings;
- recording duration and value count;
- replay target compatibility;
- active clients and live values;
- evidence availability.

### Manual Or Synthetic Source

Stage: P1

UI must make clear:

- manual schema entry point;
- synthetic pattern/range/update settings;
- deterministic settings where relevant;
- validation before save/run.

### Shared Team Operation

Stage: P1/P2

UI must make clear:

- who started a run;
- who edited or exported something;
- which actions are Admin-only;
- which object is locked and by whom;
- whether activity is runtime behavior or user audit history.

## 6. Cross-Screen Rules

These rules apply everywhere:

- Runtime state must be visible on operational screens.
- Long-running actions show progress, elapsed time, and safe retry/cancel state.
- Errors explain the cause and the next action in user terms.
- Empty states point to the next useful action.
- Destructive actions explain impact before confirmation.
- Credentials, secrets, private keys, and PKI material are never shown in
  summaries, activity, evidence, imports, or exports.
- Import/export artifacts are version-aware; unsupported newer versions fail
  safely before commit.
- Live values are an operational preview; recordings and evidence are captured
  timelines.
- Runtime events and user activity/audit are separate histories.
- Tables support search, filter, sort, no-results state, and visible active
  filters.
- Color is never the only status signal.
- Keyboard and focus behavior are designed for every interactive screen.

## 7. Contract Coverage

Covered against `SPEC.md`:

- Data sources: OPC UA, Modbus TCP, on-demand management, real-source scan,
  project save/reuse/import/export/lifecycle.
- Recordings, samples, replay: record real data, replay, import/export reusable
  data, deterministic replay/synthetic settings.
- Scenarios and faults: planned as P2 with structured builder and run view.
- Observability and evidence: live values, clients, health, runtime events,
  evidence export.
- UI/control: Web UI plus automated run visibility.
- Local/shared usage: trusted local P0, shared team P1.
- Login/access: no-login local P0, shared login P1, expanded identity/roles P2.

Covered against `ARCHITECTURE.md`:

- Web UI uses product concepts, not worker/database/IPC concepts.
- UI stays protocol-neutral except protocol-specific configuration fields.
- Live preview and captured timelines are visually distinct.
- Runtime events and user activity/audit are distinct.
- Import/export artifacts are version-aware.
- Secrets and private keys are excluded from user-visible summaries and exports.
- OAuth2/OIDC and expanded roles remain future-compatible.
- Approved frontend stack is respected.

## 8. Ready For Development

Ready:

- product UI scope;
- delivery stages;
- information architecture;
- screen inventory;
- primary workflows;
- role behavior;
- edge-state categories;
- architecture alignment;
- implementation task breakdown.

Needs refinement during implementation:

- exact OPC UA and Modbus labels;
- exact validation and error copy;
- exact table column order;
- exact responsive breakpoints;
- exact evidence file naming;
- exact artifact version compatibility copy;
- exact deterministic setting labels;
- exact retention thresholds.

## 9. Recommended Next Step

Start with P0 tasks from `UI_TASKS.md`.

Build order:

1. Foundation patterns.
2. Project entry and overview.
3. Data-source list/detail.
4. Create Data Source wizard.
5. Scan -> Record -> Replay.
6. Evidence.
7. Visual/accessibility/edge-state pass.

Do not start P1 or P2 work if it blocks completion of the P0 Scan -> Record ->
Replay slice.
