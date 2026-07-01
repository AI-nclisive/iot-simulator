# UI Task Breakdown

## Purpose

`UI_TASKS.md` turns the UI documentation into implementation work.

This file is for planning, assignment, and tracking. It keeps the UI work in one
task register without repeating the full design or screen descriptions.

## How To Read This File

Each task keeps the same compact structure:

- checkbox:
  current completion state;
- `ID`:
  stable reference for discussion and tracking;
- `Goal`:
  what this task is trying to produce;
- `Surface`:
  the page, flow, or shared surface it belongs to;
- `Work includes`:
  what should be designed or built inside this task;
- `Depends`:
  what should be in place first;
- `Done when`:
  the short acceptance target for the task.

Order rule:

- tasks are listed in recommended implementation order inside each stage;
- task `ID` is a stable reference only and does not define sequence;
- within one stage, the file should be readable from top to bottom as a build
  queue.

Task input rule:

- if `Surface` matches a named page, flow, or shared surface, read the section
  with the same name in `UI_SCREEN_SPECS.md` before starting;
- if `Surface` is a cross-surface pattern or review task, read the relevant
  sections in `DESIGN.md` plus `Cross-Surface Contract` in
  `UI_SCREEN_SPECS.md`;
- a task should be assignable without needing hidden assumptions from unrelated
  tasks.

## Stage Map

| Stage | Goal | Main workstreams |
| --- | --- | --- |
| `P0` | Core shell and primary product flow | Shell, project surfaces, scan -> record -> replay, source detail, evidence, baseline review |
| `P1` | Shared usage, reuse, and operational breadth | Login, project lifecycle, schema editing, recordings/samples, deterministic controls, admin/settings, retention, notifications |
| `P2` | Advanced shared workflows | Activity history, identity expansion, scenarios, faults |
| `INT` | Wire UI to the real backend API | API client + contract alignment, replace mocks on ready core, SSE live subscriptions |

## P0 - Core Shell And Primary Flow

### Wave 1 - Shell And Compatibility Patterns

Read first:

- `DESIGN.md`: `Shell Structure`, `Shared Usage Behavior`,
  `Interaction Rules`
- `UI_SCREEN_SPECS.md`: `Cross-Surface Contract`

Parallel execution:

1. Start `UI-001` first.
2. After `UI-001`, run `UI-002`, `UI-003`, `UI-004`, and `UI-006` in parallel.
3. After `UI-006`, run `UI-007`.

Interpretation rule:

- this wave keeps the P0 shell compatible with later shared login and
  permission behavior;
- it does not introduce visible login flow, mode switching, or shared-first
  shell controls into the local-first P0 experience.

- [x] `UI-001` App shell
  Goal: define the base shell frame that every main page will live inside.
  Surface: app shell
  Work includes: minimal top bar layout, collapsible left project rail,
  project context in the left rail, main content container behavior, and the
  rules for how this shell stays consistent across pages.
  Depends: none
  Done when: top bar, project rail, project context, and page structure are
  stable across pages.

- [x] `UI-002` Shared UI states
  Surface: cross-surface pattern
  Depends: `UI-001`
  Done when: loading, empty, error, stale, locked, warning, and status patterns
  are reusable and do not rely on color alone.

- [x] `UI-003` Table pattern
  Surface: cross-surface pattern
  Depends: `UI-001`
  Done when: search, filter, sort, active filters, row actions, and no-results
  states are defined for dense operational tables.

- [x] `UI-004` Confirmation pattern
  Surface: cross-surface pattern
  Depends: `UI-001`
  Done when: destructive and disruptive confirmations clearly explain impact,
  affected objects, and reversibility.

- [x] `UI-006` Role-aware UI pattern
  Goal: keep P0 action surfaces compatible with later shared permissions
  without turning shared controls into top-level shell UI.
  Surface: cross-surface pattern
  Work includes: action-level permission states, restricted-route treatment,
  and rules for where shared-role differences appear when authentication is
  added later.
  Depends: `UI-001`
  Done when: Admin and User differences can be added to shared action surfaces
  later without redesigning the local-first shell or page structure.

- [x] `UI-007` Local vs shared mode behavior
  Goal: preserve immediate trusted-local entry now while keeping a clean
  insertion point for later shared authentication.
  Surface: cross-surface pattern
  Work includes: local-first entry assumptions, shared login insertion before
  project content, stable post-login handoff into the same project shell, and
  permission injection points that do not require a second navigation model.
  Depends: `UI-001`, `UI-006`
  Done when: local entry stays immediate, and shared authentication plus
  permissions can be added in P1 without changing project navigation, shell
  layout, or page structure.

### Wave 2 - Project Shell

Read first:

- `DESIGN.md`: `Shell Structure`, `Entry Flow`, `Core Flow`
- `UI_SCREEN_SPECS.md`: `Project Entry`, `Project Overview`,
  `Data Sources List`, `Runtime Dashboard`

Parallel execution:

1. After Wave 1, run `UI-011`, `UI-021`, and `UI-022` in parallel.
2. After `UI-011` and `UI-022`, run `UI-020`.

- [x] `UI-011` Project Entry
  Surface: `Project Entry`
  Depends: `UI-001`, `UI-003`, `UI-006`
  Done when: users can open a project quickly, see recent activity, and shared
  mode keeps create/import actions Admin-only.

- [x] `UI-022` Runtime Dashboard
  Surface: `Runtime Dashboard`
  Depends: `UI-001`, `UI-002`
  Done when: active runs, relevant process context, initiator context,
  parameter-scale context, and per-run evidence state stay visible on
  `Overview` without following the user onto every project page.

- [x] `UI-020` Project Overview
  Surface: `Project Overview`
  Depends: `UI-001`, `UI-002`, `UI-003`, `UI-006`
  Done when: the landing route stays minimal, serves as the runtime command
  surface, and adds page content only when it improves orientation.

- [x] `UI-021` Data Sources List
  Surface: `Data Sources List`
  Depends: `UI-003`, `UI-004`, `UI-006`
  Done when: each row exposes protocol, endpoint, status, health, client
  context, and role-aware row actions.

### Wave 3 - Source Creation And Primary Flow

Read first:

- `DESIGN.md`: `Core Flow`, `Source Creation Model`, `Wizard Structure`,
  `Scan Path`, `Record Path`, `Replay Path`
- `UI_SCREEN_SPECS.md`: `Create Data Source Wizard`, `Scan Real Source`,
  `Recording Flow`, `Replay Flow`, `Credential Handling`

Parallel execution:

1. Start `UI-030` first.
2. After `UI-030`, run `UI-031` and `UI-032` in parallel.
3. After `UI-031`, run `UI-039`.
4. After `UI-032`, run `UI-033`.
5. After `UI-033`, run `UI-034`.
6. After `UI-034`, run `UI-035`.
7. After `UI-033` and `UI-039`, run `UI-039A`.

- [x] `UI-030` Create Data Source wizard shell
  Surface: `Create Data Source Wizard`
  Depends: `UI-001`, `UI-002`, `UI-006`
  Done when: one guided flow supports create, back, next, cancel, and review
  without losing user input.

- [x] `UI-031` Protocol selection step
  Surface: `Create Data Source Wizard`
  Depends: `UI-030`
  Done when: current and future protocols fit one stable selection pattern.

- [x] `UI-039` OPC UA and Modbus field baseline
  Surface: source creation protocol fields
  Depends: `UI-031`
  Done when: initial protocol-specific fields are represented consistently
  inside the shared wizard model.

- [x] `UI-032` Source basis step
  Surface: `Create Data Source Wizard`
  Depends: `UI-030`
  Done when: scan is the promoted path, while manual, prepared-data, and
  synthetic paths remain clear and first-class.

- [x] `UI-033` Scan branch
  Surface: `Scan Real Source`
  Depends: `UI-030`
  Done when: connection test, scan progress, retry, partial discovery, large
  schema, and unknown-type states are handled clearly.

- [x] `UI-034` Recording flow
  Surface: `Recording Flow`
  Depends: `UI-033`
  Done when: recording state, duration, value count, disconnects, no-values, and
  partial-save states are understandable.

- [x] `UI-035` Replay flow
  Surface: `Replay Flow`
  Depends: `UI-034`
  Done when: target compatibility, active-target impact, no-client state,
  replay progress, and completion or failure states are clear.

- [x] `UI-039A` Real-source credential handling
  Surface: `Credential Handling`
  Depends: `UI-033`, `UI-039`
  Done when: sensitive fields are masked, persistence behavior is visible, and
  secret values never leak into summaries or exports.

### Wave 4 - Source Detail

Read first:

- `DESIGN.md`: `Observe Path`, `Interaction Rules`
- `UI_SCREEN_SPECS.md`: `Data Source Detail`

Parallel execution:

1. Start `UI-040` first.
2. After `UI-040`, prioritize `UI-044` first because large parameter sets are
   the core detail-surface requirement.
3. After `UI-044` starts, run `UI-041` and `UI-047` in parallel where team
   capacity allows.
4. After `UI-035`, run `UI-054`.

- [x] `UI-040` Data Source Detail shell
  Surface: `Data Source Detail`
  Depends: `UI-021`
  Done when: Overview, Schema, Values, Clients, Events, and Settings are
  available inside one stable detail surface, and the source clearly shows that
  one runtime may contain many parameters.

- [x] `UI-041` Source overview tab
  Surface: `Data Source Detail`
  Depends: `UI-040`
  Done when: state, endpoint, health, active behavior, and primary actions are
  visible without leaving the detail page.

- [x] `UI-044` Values tab
  Surface: `Data Source Detail`
  Depends: `UI-040`
  Done when: live values, timestamps, current mode, and stale state are clearly
  separated from captured artifacts, and large parameter sets remain searchable,
  filterable, and readable.

- [x] `UI-047` Settings tab
  Surface: `Data Source Detail`
  Depends: `UI-040`
  Done when: source-level configuration, validation, and safe editing boundaries
  are represented cleanly.

- [x] `UI-054` Assign-to-replay action
  Surface: `Replay Flow`
  Depends: `UI-034`, `UI-035`
  Done when: assigning a recording or sample back to a source shows
  compatibility and replacement impact before runtime starts.

### Wave 5 - Evidence

Read first:

- `DESIGN.md`: `Evidence Path`, `Interaction Rules`
- `UI_SCREEN_SPECS.md`: `Evidence List`, `Evidence Detail`

Parallel execution:

1. Start `UI-070` first.
2. After `UI-070`, run `UI-071`.
3. After `UI-071`, run `UI-072`.
4. After `UI-072`, run `UI-073`.

- [x] `UI-070` Evidence List
  Surface: `Evidence List`
  Depends: `UI-003`, `UI-006`
  Done when: users can find evidence by source, initiator, project, scenario,
  and state; export remains role-aware.

- [x] `UI-071` Evidence Detail
  Surface: `Evidence Detail`
  Depends: `UI-070`
  Done when: summary, timeline, clients, faults or errors, and partial states
  explain what happened and how complete the artifact is.

- [x] `UI-072` Evidence export dialog
  Surface: `Evidence Detail`
  Depends: `UI-071`
  Done when: export formats, artifact scope, and secret exclusion are explicit.

- [x] `UI-073` Evidence export failure recovery
  Surface: `Evidence Detail`
  Depends: `UI-072`
  Done when: export failure states support retry with a clear reason and next
  action.

### Wave 6 - P0 Review

Read first:

- `DESIGN.md`: `Visual Direction`, `Accessibility`
- `UI_SCREEN_SPECS.md`: `Cross-Surface Contract`

Parallel execution:

1. Start `UI-090` first.
2. After `UI-090`, run `UI-091` and `UI-092` in parallel.

- [x] `UI-090` Visual system baseline
  Surface: cross-surface pattern
  Depends: `UI-001`, `UI-002`, `UI-003`, `UI-004`
  Done when: typography, spacing, forms, tables, dialogs, status treatment, and
  shared component rules are defined for the approved stack.

- [x] `UI-091` Accessibility review
  Surface: cross-surface review
  Depends: `UI-001`, `UI-002`, `UI-090`
  Done when: keyboard flow, focus visibility, labels, status messaging, and
  contrast are checked on the core P0 path.

- [x] `UI-092` Edge-state review
  Surface: cross-surface review
  Depends: `UI-001`, `UI-002`, `UI-090`
  Done when: empty, loading, locked, permission, stale, partial, and error
  states are covered across the primary flow.

## P1 - Shared Usage, Reuse, And Operational Breadth

### Wave 1 - Access, Locks, And Project Lifecycle

Read first:

- `DESIGN.md`: `Entry Flow`, `Shared Usage Behavior`,
  `Imports And Exports`
- `UI_SCREEN_SPECS.md`: `Login`, `Project Entry`, `Settings`

Parallel execution:

1. After P0 shell completion, run `UI-005` and `UI-010` in parallel.
2. After `UI-011` plus the required shared patterns, run `UI-012`, `UI-013`,
   and `UI-014` in parallel.

- [x] `UI-005` Edit-lock pattern
  Surface: cross-surface pattern
  Depends: `UI-001`
  Done when: lock ownership, read-only mode, and stale-lock recovery are defined
  for shared editing.

- [x] `UI-010` Login screen
  Surface: `Login`
  Depends: `UI-001`, `UI-007`
  Done when: loading, invalid credentials, server failure, and session-expired
  states work cleanly in shared mode.

- [x] `UI-012` Project import flow
  Surface: `Project Entry` / `Settings`
  Depends: `UI-004`, `UI-011`
  Done when: progress, failure, overwrite impact, and version compatibility are
  visible before import is committed.

- [x] `UI-013` Project lifecycle actions
  Surface: `Project Entry` / `Settings`
  Depends: `UI-004`, `UI-006`, `UI-011`
  Done when: rename, duplicate, archive, and delete flows are clear and expose
  shared impact before destructive changes.

- [x] `UI-014` Project export flow
  Surface: `Settings`
  Depends: `UI-006`, `UI-011`
  Done when: export scope, completion state, failure state, and secret exclusion
  are visible.

### Wave 2 - Operational Breadth

Read first:

- `DESIGN.md`: `Observe Path`, `Shared Usage Behavior`
- `UI_SCREEN_SPECS.md`: `Data Source Detail`, `Automated Run Visibility`

Parallel execution:

1. After the required P0 detail and runtime tasks, run `UI-025`, `UI-045`, and
   `UI-046` in parallel.

- [x] `UI-025` Automated run visibility
  Surface: `Automated Run Visibility`
  Depends: `UI-020`, `UI-022`
  Done when: automation-driven runs are clearly labeled and visible in the same
  places as equivalent manual runs.

- [x] `UI-045` Clients tab
  Surface: `Data Source Detail`
  Depends: `UI-040`
  Done when: connected-client state and connection lifecycle are easy to inspect.

- [x] `UI-046` Events tab
  Surface: `Data Source Detail`
  Depends: `UI-040`, `UI-022`
  Done when: runtime events are filterable and remain distinct from user
  activity history.

### Wave 3 - Creation, Editing, And Reuse

Read first:

- `DESIGN.md`: `Alternative Flows`, `Schema Review And Editing`,
  `Imports And Exports`
- `UI_SCREEN_SPECS.md`: `Create Data Source Wizard`, `Full Schema Editor`,
  `Recordings & Samples`, `Deterministic Run Settings`

Parallel execution:

1. After `UI-030`, run `UI-036`, `UI-037`, and `UI-038` in parallel.
2. After `UI-040` and `UI-005`, run `UI-042`.
3. After `UI-042`, run `UI-043`.
4. After `UI-003`, run `UI-050`.
5. After `UI-050`, run `UI-051`, `UI-052`, and `UI-053` in parallel.
6. After `UI-035` and `UI-037`, run `UI-055`.

- [x] `UI-036` Manual source branch
  Surface: `Create Data Source Wizard`
  Depends: `UI-030`
  Done when: manual structure creation has a clear branch and a clean handoff
  into the full schema editor where needed.

- [x] `UI-037` Synthetic source branch
  Surface: `Create Data Source Wizard`
  Depends: `UI-030`
  Done when: pattern, range, update behavior, and repeatability controls are
  represented in one coherent path.

- [x] `UI-038` Prepared-data branch
  Surface: `Create Data Source Wizard`
  Depends: `UI-030`
  Done when: prepared recording or sample input shows compatibility and preview
  before creation.

- [x] `UI-042` Full Schema Editor
  Surface: `Full Schema Editor`
  Depends: `UI-040`, `UI-005`
  Done when: structure tree or table, details panel, validation, unsaved
  changes, and shared-edit protection work together as one editor surface for
  small and very large schemas.

- [x] `UI-043` Schema dependency warnings
  Surface: `Full Schema Editor`
  Depends: `UI-042`, `UI-004`
  Done when: identifier, type, and dependency impact is visible before save.

- [x] `UI-050` Recordings & Samples list
  Surface: `Recordings & Samples`
  Depends: `UI-003`
  Done when: reusable artifacts are easy to browse by name, source, type,
  origin, duration, tags, and last use.

- [x] `UI-051` Recording and sample import
  Surface: `Recordings & Samples`
  Depends: `UI-050`
  Done when: validation, preview, unsupported artifact handling, and
  newer-than-supported protection are safe and understandable.

- [x] `UI-052` Recording and sample preview
  Surface: `Recordings & Samples`
  Depends: `UI-050`
  Done when: timeline preview, value count, and warnings help the user judge
  fitness for replay.

- [x] `UI-053` Recording and sample export
  Surface: `Recordings & Samples`
  Depends: `UI-006`, `UI-050`
  Done when: reusable artifacts can be exported with clear scope and role-aware
  permissions.

- [x] `UI-055` Deterministic run settings
  Surface: `Deterministic Run Settings`
  Depends: `UI-035`, `UI-037`
  Done when: seed or preset, ordering or timing mode, repeatability scope, and
  evidence traceability are clearly represented.

### Wave 4 - Settings, Admin, And Feedback

Read first:

- `DESIGN.md`: `Shared Usage Behavior`, `Failure Handling`,
  `Visual Direction`
- `UI_SCREEN_SPECS.md`: `Settings`, `Admin UI`, `Retention & Cleanup`,
  `Notifications`

Parallel execution:

1. After the required P0 and P1 foundations, run `UI-080`, `UI-081`, `UI-094`,
   and `UI-095` in parallel.
2. After `UI-081`, run `UI-082` and `UI-083` in parallel.
3. After `UI-050` and `UI-070`, run `UI-074`.
4. After `UI-020`, `UI-030`, and `UI-040`, run `UI-093`.

- [ ] `UI-074` Retention and cleanup UI
  Surface: `Retention & Cleanup`
  Depends: `UI-050`, `UI-070`
  Done when: size, age, last use, dependency impact, and cleanup-failure states
  are visible before destructive cleanup.

- [x] `UI-080` Settings page
  Surface: `Settings`
  Depends: `UI-001`, `UI-006`
  Done when: project settings and environment settings are separated and
  mutation remains role-aware.

- [x] `UI-081` Admin users list
  Surface: `Admin UI`
  Depends: `UI-001`, `UI-003`, `UI-006`
  Done when: the Admin surface clearly shows users, roles, status, and recent
  activity hints.

- [x] `UI-082` Access management flows
  Surface: `Admin UI`
  Depends: `UI-081`
  Done when: role assignment, status validation, and save states are clear
  without assuming product-owned password lifecycle management.

- [x] `UI-083` Role-change behavior
  Surface: `Admin UI`
  Depends: `UI-081`
  Done when: role changes are confirmed and traceable in shared activity.

- [x] `UI-093` End-to-end prototype review
  Surface: cross-surface review
  Depends: `UI-020`, `UI-030`, `UI-040`
  Done when: the team can walk through the primary path end to end and identify
  friction before implementation expands.

- [x] `UI-094` Responsive and platform baseline
  Surface: cross-surface review
  Depends: `UI-001`, `UI-090`
  Done when: desktop-first behavior, tablet tolerance, phone limits, and
  browser consistency across Linux, Windows, and macOS are explicitly decided.

- [x] `UI-095` Notification pattern
  Surface: `Notifications`
  Depends: `UI-001`, `UI-002`
  Done when: success, warning, error, stale, reconnecting, and shared-impact
  feedback are visually and behaviorally consistent.

## P2 - Advanced Shared Workflows

### Wave 1 - Activity And Identity

Read first:

- `DESIGN.md`: `Shared Usage Behavior`
- `UI_SCREEN_SPECS.md`: `Activity View`, `Login`, `Admin UI`

Parallel execution:

1. After the required shared-mode foundations, run `UI-023` and `UI-084` in
   parallel.
2. After `UI-023`, run `UI-024`.

- [ ] `UI-023` Team activity feed
  Surface: `Project Overview`
  Depends: `UI-001`
  Done when: actor, action, object, time, and drill-in target are visible in a
  compact team activity surface.

- [ ] `UI-024` Activity history view
  Surface: `Activity View`
  Depends: `UI-003`, `UI-023`
  Done when: users can filter history by actor, action, object, project, and
  time without mixing audit and runtime streams.

- [ ] `UI-084` Identity-provider and expanded-role compatibility
  Surface: `Login` / `Admin UI`
  Depends: `UI-010`, `UI-081`
  Done when: login and role display can grow into provider metadata and a larger
  shared-role model without a UI redesign.

### Wave 2 - Scenarios And Faults

Read first:

- `DESIGN.md`: `Alternative Flows`, `Shared Usage Behavior`
- `UI_SCREEN_SPECS.md`: `Scenarios`, `Scenario Builder`,
  `Scenario Run View`

Parallel execution:

1. Start `UI-060` first.
2. After `UI-060`, run `UI-061`.
3. After `UI-061`, run `UI-062`, `UI-064`, and `UI-065` in parallel.
4. After `UI-062`, run `UI-063`.

- [x] `UI-060` Scenarios
  Surface: `Scenarios`
  Depends: `UI-003`, `UI-006`
  Done when: saved scenarios show state, last run, owner or editor context, and
  role-aware actions from one landing page.

- [x] `UI-061` Scenario builder shell
  Surface: `Scenario Builder`
  Depends: `UI-005`, `UI-006`, `UI-060`
  Done when: the builder provides step list, details panel, validation, and
  save/run structure inside one coherent editing surface.

- [x] `UI-062` Scenario step editor
  Surface: `Scenario Builder`
  Depends: `UI-061`
  Done when: start, stop, replay, synthetic, fault, wait, and marker steps fit
  the same step-editing model.

- [x] `UI-063` Fault configuration
  Surface: `Scenario Builder`
  Depends: `UI-062`
  Done when: target, timing, parameters, and clear behavior are understandable
  before the fault is added.

- [x] `UI-064` Scenario validation
  Surface: `Scenario Builder`
  Depends: `UI-061`
  Done when: run is blocked until validation passes, and the reason is obvious.

- [x] `UI-065` Scenario Run View
  Surface: `Scenario Run View`
  Depends: `UI-022`, `UI-061`
  Done when: current step, initiator, involved sources, faults, clients, events,
  and evidence state are visible during the run.

## INT - Wire UI To Real Backend

The P0/P1 surfaces above were built against mock fixtures (`frontend/src/**/mock-*.ts`)
with no API client. This stage replaces those mocks with the real `/api/v1`
backend. The field-by-field join, naming/enum gaps, and which surfaces are
backable today live in `docs/FRONTEND_BACKEND_CONTRACT_MAP.md` — read it first.

Read first:

- `docs/FRONTEND_BACKEND_CONTRACT_MAP.md` (FE↔BE contract map + gap index)
- `UI_SCREEN_SPECS.md`: `Cross-Surface Contract`

Parallel execution:

1. Start `UI-096` first (everything depends on the client + contract alignment).
2. After `UI-096`, run `UI-097` (ready-core REST) and — once backend SSE lands —
   `UI-098` (live streams) in parallel.

- [x] `UI-096` Backend API client and contract alignment
  Goal: replace mock fixtures with a typed client generated from the backend
  OpenAPI, and resolve the naming/enum gaps documented in the contract map.
  Surface: cross-surface foundation
  Work includes: `VITE_API_BASE_URL` + dev proxy in `vite.config.ts`; generated
  TS client/types from `/openapi.json`; a shared request layer that captures
  `ETag` on reads and sends `If-Match` on writes; `application/problem+json` →
  shared error/toast mapping; `Authorization: Bearer` injection in shared mode;
  enum mappers (protocol, `runtimeState` → status/health, `dataType` → type).
  Depends: `UI-001`, `UI-002`, `UI-090`
  Done when: a typed client talks to `/api/v1`, ETag/If-Match round-trips,
  errors surface through shared patterns, and no ready-core surface imports
  `mock-*.ts` for live data.

- [x] `UI-097` Wire ready-core surfaces to live API
  Goal: switch projects, data sources, schema, scan, recording capture, and
  replay from Zustand mocks to the real endpoints.
  Surface: `Project Entry`, `Data Sources List`, `Data Source Detail`,
  `Full Schema Editor`, `Create Data Source Wizard` (scan branch),
  `Recording Flow`, `Replay Flow`
  Work includes: projects CRUD; data-source CRUD + start/stop + credential
  clear; schema get/save (round-trip `kind`/`parentId`/`valueRank`/`access`);
  async scan with `jobId` polling + type-resolution create; recording capture
  start/stop + list; fire-and-return replay; derive `parameterCount`/source
  counts where the backend exposes no field (per the contract map).
  Depends: `UI-096`
  Done when: the `Scan → Record → Replay` path and core CRUD run against
  `/api/v1` with mocks removed; gaps with no backend field are clearly stubbed
  and flagged, never faked silently.

- [x] `UI-098` Live SSE subscriptions
  Goal: drive live surfaces from server-sent events instead of static mocks.
  Surface: `Data Source Detail` (Values/Clients/Events), `Runtime Dashboard`,
  `Project Overview`
  Work includes: an `EventSource` subscription layer with reconnect/backoff and
  stale handling (`UI-002`/`UI-095`); bind live values, connected clients,
  runtime events, and runtime/overview state; remove the corresponding mock
  fixtures.
  Depends: `UI-096`; backend `IS-046` (SSE infra) + `IS-051`/`IS-052`/`IS-053`/`IS-054`/`IS-055`
  Done when: live tabs and the runtime dashboard reflect real-time backend state
  with graceful reconnect, and no mock live data remains on those surfaces.

- [x] `UI-099` Align SchemaParameter mock shape to NodeDto
  Goal: remove fields not in NodeDto from SchemaParameter and fix all consumers.
  Fields removed: `min`, `max`, `hasDependent`.
  Surfaces: Schema editor.
  Done when: SchemaParameter has only fields present in NodeDto; schema editor shows type/unit/description only; detectDependencyWarnings reduced to description-change only; no TypeScript errors.

- [x] `UI-100` Align DataSourceRow mock shape to DataSourceResponse
  Goal: remove fields not in DataSourceResponse from DataSourceRow and fix all consumers.
  Fields removed: `process`, `clients`, `lastOperator`, `assignedReplayArtifactId`.
  Surfaces: Data Sources List, Data Source Detail (Overview, header), Recording Flow, Replay Flow.
  Done when: DataSourceRow has only fields present in DataSourceResponse; all list/detail/flow surfaces work without those fields; store methods that only mutated removed fields are deleted; no TypeScript errors.

- [x] `UI-101` Align RecordingRow and ReusableArtifact mock shapes to RecordingResponse
  Goal: remove fields not in RecordingResponse from RecordingRow and ReusableArtifact and fix all consumers.
  Fields removed from RecordingRow: `name`, `type` (ArtifactType), `sourceName`, `protocol`, `duration`, `tags`, `lastUsedAt`, `sizeKb`; origin reduced to `"captured" | "imported"` (remove "synthetic"). Added: `valueCount`.
  Fields removed from ReusableArtifact: `name`, `type`, `protocol`, `sourceName`, `durationLabel`, `status`.
  Surfaces: Recordings page, Recording Export dialog, Recording Import dialog, Replay Flow.
  Done when: both types contain only fields present in RecordingResponse; recordings page, export/import dialogs, and replay flow work without removed fields; no TypeScript errors.

- [x] `UI-102` Align SourceValueRow mock shape to backend DataType enum
  Goal: remove `"enum"` from `SourceValueRow.dataType` and replace with `"string"` to match backend contract.
  Fields changed: `dataType` union `"float" | "int" | "bool" | "enum"` → `"float" | "int" | "bool" | "string"`; all mock rows updated.
  Surfaces: Data Source Detail — Values tab.
  Done when: `SourceValueRow.dataType` contains only values present in backend `DataType` contract (`FLOAT32/64→float`, `INT*→int`, `BOOL→bool`, `STRING→string`); no `"enum"` in mock data; no TypeScript errors.

- [x] `UI-103` Wire duplicate project to POST /projects/{id}/duplicate
  Goal: replace the `/copy` stub + fallback in `projects-store.ts` with the canonical `/duplicate` endpoint delivered by IS-071.
  Surfaces: Projects list — duplicate action.
  Depends: backend IS-071.
  Done when: `duplicateProject` calls `POST /api/v1/projects/{id}/duplicate` directly; fallback create path removed; no TypeScript errors.

- [x] `UI-104` Wire duplicate data source to POST /data-sources/{id}/duplicate
  Goal: replace the manual `POST /data-sources` create in `data-sources-store.ts` with the canonical `/duplicate` endpoint delivered by IS-066.
  Surfaces: Data Sources list — duplicate action.
  Depends: backend IS-066.
  Done when: `duplicateDataSource` calls `POST /api/v1/projects/{pid}/data-sources/{rowId}/duplicate` directly; manual create path removed; no TypeScript errors.
- [x] `UI-105` Wire Evidence surfaces to live API
  Goal: replace mock-evidence.ts with real backend calls on Evidence List and Evidence Detail.
  Surface: `Evidence List`, `Evidence Detail`
  Work includes: GET /api/v1/projects/{projectId}/evidence (list); GET /{id} (detail, parse manifest JsonNode); POST /{id}/export?format=BUNDLE + GET /{id}/download (export/download); status mapping CAPTURING→"In progress", READY→"Ready", PARTIAL→"Incomplete", EXPORT_FAILED→"Export failed"; remove mock-evidence.ts.
  Depends: `UI-096`; spike on manifest JSON shape from EvidenceService.
  Done when: Evidence list and detail load from /api/v1; export/download work; mock-evidence.ts removed; no TypeScript errors.

- [x] `UI-106` Wire Events tab to runtime-events API + SSE
  Goal: replace mock-source-events.ts with real backend history + live SSE events on the Events tab.
  Surface: `Data Source Detail` — Events tab
  Work includes: GET /api/v1/projects/{projectId}/runtime-events?source={sourceId} for history; live append from existing use-live-runtime SSE hook (filter by dataSourceId); type→level mapping (SOURCE_ERROR/ERROR→error, SOURCE_STALE→warning, rest→info); type→category mapping; remove mock-source-events.ts.
  Depends: `UI-096`; backend IS-055 (✅).
  Done when: Events tab shows real runtime events; live events append without duplicates; mock-source-events.ts removed; no TypeScript errors.

- [x] `UI-107` Wire project overview counts to /projects/overview
  Goal: replace hardcoded-0 count badges in projects-store.ts with data from GET /api/v1/projects/overview.
  Surface: `Project Entry` — source/artifact count badges
  Work includes: call GET /api/v1/projects/overview after loadProjects; merge by projectId into ProjectSummary; expose sourcesNeedingAttention as optional badge; lastActivity stays updatedAt (no backend field — comment clearly).
  Depends: `UI-096`; backend IS-054 (✅).
  Done when: configuredSources, runningSources, reusableArtifacts reflect real backend data; no TypeScript errors.

- [x] `UI-108` Wire Recordings page to live store + add Samples surface
  Goal: switch recordings-page.tsx from mockRecordings to artifacts-store (already calls live API); add Samples surface.
  Surface: `Recordings & Samples`
  Work includes: recordings-page.tsx reads from useArtifactsStore instead of mockRecordings; remove mock-recordings.ts import; add samples methods to artifacts-store.ts (GET/POST/DELETE /api/v1/projects/{pid}/samples); add Samples section to recordings page; SampleResponse = {id, projectId, derivedFromRecordingId, name, selection, tags[], createdAt, createdBy, version}.
  Depends: `UI-096`; backend IS-068 (✅).
  Done when: recordings load from store (live API); samples list/create/delete work; mock-recordings.ts no longer imported; no TypeScript errors.

- [x] `UI-109` Fix schema editor round-trip — preserve all NodeDto fields on save
  Goal: schema PUT currently drops parentId/kind/dataType/valueRank/access; only saves unit+description for selected param. This corrupts folder structure and type metadata on every save.
  Surface: `Data Source Detail` — Schema tab
  Work includes: store raw NodeDto[] from GET in editor state alongside EditBuffer; executeSave builds PUT payload from original NodeDto + applied edits — nothing dropped; DataType mapping on save: float→FLOAT64, int→INT32, bool→BOOL, string→STRING; BYTES/DATETIME preserved as-is (no FE type, read-only); ETag/If-Match correctly threaded.
  Depends: `UI-096`.
  Done when: GET→PUT round-trip preserves FOLDER nodes, parentId, valueRank, access; editing unit/description works; BYTES/DATETIME shown read-only; no TypeScript errors.

- [x] `UI-110` Create Project flow (modal)
  Goal: the `Create project` button on `Project Entry` navigates to `/projects/create`, which renders only a `SurfaceStubPage` placeholder — no form, so nothing happens. The `createProject(name, description)` store action (`POST /api/v1/projects`) and the backend endpoint already exist (UI-097); only the creation surface is missing. Add a real surface as a modal, mirroring the existing `ImportProjectDialog`.
  Surface: `Project Entry` — Create project action.
  Work includes: add a `CreateProjectDialog` modal in `project-entry-page.tsx` (React portal, parallel to `ImportProjectDialog`); name (required, trimmed, non-empty) + optional description fields with inline validation; wire confirm to the existing `createProject` store action; disabled/loading state while the POST is in flight; surface failures via the notification store (`push`) like the other lifecycle actions; on success close the dialog and open the new project (`setCurrentProjectId` + navigate to `/overview`); replace both `navigate("/projects/create")` call sites (header button + empty-state action) with opening the modal; remove the now-dead `/projects/create` stub route, `entrySurfaceContent.projectCreate`, and the `SurfaceStubPage` import if it becomes unused; keep Admin-only gating via `access.canCreateProject`; add a test covering validation + successful submit.
  Depends: `UI-011`, `UI-097`.
  Done when: clicking `Create project` opens a modal; submitting a valid name calls `POST /api/v1/projects`, the new project opens (and appears in the list), and errors are shown without closing the dialog; the `/projects/create` stub route is removed; tests pass and there are no TypeScript errors.

- [x] `UI-111` Wire dashboard active-runs panel to live API
  Goal: `RuntimeDashboardPanel` currently imports `activeRuns` directly from `mock-workspace` — hardcoded fake data. Wire it to the real `GET /api/v1/projects/{id}/active-runs` endpoint (IS-122).
  Surface: `Runtime Dashboard` — active-runs list.
  Work includes: `useActiveRuns(projectId)` hook polling every 5 s; replace mock import with hook result; loading skeleton; empty state when no runs; error handling.
  Depends: IS-122.
  Done when: overview shows real active runs from backend; no mock data imported in the component; empty state renders correctly; no TypeScript errors.

- [x] `UI-112` Recording flow — real SSE value stream
  Goal: Replace the fake `setInterval` value counter in `RecordingFlowPage` with a real SSE subscription via `useLiveValues`.
  Surface: `Recording flow page` — capture metrics (value count, last received, state transitions).
  Work includes: remove fake timer; wire `useLiveValues(sourceId, captureActive)`; sync `valueCount` from SSE rows; transition `recordingState` from SSE `liveStatus`; remove "Simulate disconnect" debug button.
  Depends: IS-051 (values SSE stream).
  Done when: no fake timer in `RecordingFlowPage`; value count and state reflect real SSE; TypeScript errors pass.

## Recommended Sequence

1. Complete the P0 shell and shared-pattern tasks first.
2. Finish the P0 shell pages so project navigation and runtime visibility are stable.
3. Build the primary `scan -> record -> replay` flow end to end.
4. Finish source detail and evidence so the core workflow closes cleanly.
5. Add P1 shared access, reuse flows, full editing, and operational breadth.
6. Add P1 admin, settings, retention, notifications, and platform review.
7. Move to P2 activity, identity expansion, scenarios, and faults.

The task order above already follows this sequence. The section is kept only as
the short executive summary.

