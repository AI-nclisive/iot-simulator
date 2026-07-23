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

- [x] `UI-023` Team activity feed
  Surface: `Project Overview`
  Depends: `UI-001`
  Done when: actor, action, object, time, and drill-in target are visible in a
  compact team activity surface.

- [x] `UI-024` Activity history view
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

- [x] `UI-124` Recording wizard — scan type step (schema only vs schema + data): new step between Data source and Review; two options (Schema + data default, Schema only); selected value sent as `scanType` in `POST /recordings` body; review step shows chosen type. BE half: IS-138.
- [x] `UI-123` Recording schema tab — default Schema tab in RecordingDetailPage; calls `GET .../schema`; renders collapsible folder/variable tree; loading, error, and empty states.
- [x] `UI-122` Recording value browser — filter panel (search, quality, time range): quality checkboxes (GOOD/UNCERTAIN/BAD, all checked by default); debounced search input (300 ms); from/to datetime range; passes params to `GET .../values`; empty-state when no quality selected (no API call). BE half: IS-136.
- [x] `UI-121` Remove manual basis from Create Data Source wizard
  Goal: hide the «Manual» source-basis option — not needed in current scope; SCAN / IMPORT / SYNTHETIC remain.
  Work includes: remove Manual card from basis step; remove dead conditional branches that handle manual-only flows (e.g. schema-editor auto-open); update tests.
  Done when: Manual is not selectable; SCAN / IMPORT / SYNTHETIC paths unchanged; TS build + tests pass.
- [x] `UI-123` Recording schema tab — default Schema tab in RecordingDetailPage; calls `GET .../schema`; renders collapsible folder/variable tree; loading, error, and empty states.
- [x] `UI-119` Recording value browser — paginated table (Timestamp, Parameter path, Value, Quality) in Recording detail Values tab; wired to IS-134 `GET .../values` endpoint; cursor pagination "Load more"; replaces "will be available in a future release" placeholder.
- [x] `UI-125` Replace Start button with Record / Simulate actions
  Goal: remove the bare Start action from the data-sources list and detail; replace with two explicit runtime actions: Record (opens recording flow) and Simulate (opens recording picker → starts live replay).
  Surface: `Data Sources List`, `Data Source Detail`
  Work includes: remove `startDataSource` store action + `POST .../start` call; add Record action → navigate to existing recording-flow route; add Simulate action → modal picker listing recordings for this DS (pre-select last-used from `runtimeConfig`); on confirm POST .../replay; remove Start button from `data-source-detail-preview-page.tsx` and `data-sources-list-page.tsx`; remove auto-start from `create-data-source-wizard-page.tsx`.
  Depends: IS-139, IS-140, UI-126.
  Done when: no bare Start button anywhere; Record leads to recording flow; Simulate opens picker and starts live replay; TypeScript build + vitest pass.

- [x] `UI-126` Live simulation controls: RUNNING indicator + Stop
  Goal: when a data source is actively simulating (run RUNNING), show a live indicator and Stop action that ends the run.
  Surface: `Data Sources List`, `Data Source Detail`
  Work includes: detect RUNNING replay run for this source from active-runs SSE/poll; show "Simulating" badge with recording name in list row and detail header; Stop button → POST /api/v1/runs/{runId}/stop; SSE state update removes badge on completion.
  Depends: IS-140.
  Done when: RUNNING simulation visible in list + detail; Stop ends simulation and clears badge; no stale badge after stop; TypeScript build + vitest pass.

- [x] `UI-128` Scenario step editor — real source/recording pickers + server-side validation display
  Goal: replace mock data-source and recording lists in the step editor with live store data; wire server validation issues (fetched after each save) into the builder validation summary; fix FE↔BE field contracts for SYNTHETIC (pattern→seconds/durationMs), REPLAY (compatibilityAck), and MARKER (note→label).
  Surface: `Scenario Builder`
  Work includes: `scenario-step-editor.tsx` — use `useDataSourcesStore`/`useArtifactsStore`, filter recordings by selected source, handle checkbox field kind, `projectId` prop; `scenario-steps.ts` — add checkbox to StepFieldKind, fix SYNTHETIC/REPLAY/MARKER field specs; `scenarios-api.ts` — fix toApiStep/fromApiStep for SYNTHETIC, REPLAY, MARKER; `scenarios-store.ts` — add `serverIssues` state, fetch validate after save; `scenario-builder-page.tsx` — pass projectId to editor, merge server issues with client validation.
  Depends: UI-127, IS-136 (recordings filter by sourceId).
  Done when: source/recording selects load from API; server issues merged into builder validation summary; field contracts correct; typecheck + vitest pass.

- [x] `UI-127` Wire scenario CRUD + validate to live backend API
  Goal: replace in-memory mock store with real API calls for scenarios CRUD and validation. Keep run/stop as no-ops (wired later in UI-129).
  Surface: `Scenarios`, `Scenario Builder`
  Work includes: `scenarios-api.ts` with FE↔BE mappers (type case, sourceId↔targetSourceId, params encoding); async Zustand store with loadScenarios/createScenario/renameScenario/duplicateScenario/deleteScenario/saveScenarioSteps; load-on-mount in scenarios-page and builder; Save button in builder with loading state; loading/error states in scenarios-page.
  Depends: IS-136 scenarios API.
  Done when: scenarios page loads from backend; create/rename/duplicate/delete/save call API; FE↔BE step mapper handles all step types; TypeScript build + vitest pass.

- [x] `UI-136` Data source values tab — stopped-state panel when source is not running
  Goal: values tab showed zero rows and a misleading "no visible runtime rows" message for stopped sources, because it filtered mock data by real backend source IDs that never matched.
  Fix: when source is stopped, render a clear "Source is not running" info panel instead of the empty table. Remove dependency on mock static store for the stopped path. Live values still stream from SSE when source is active.
  Done when: stopped source shows informative panel with start instructions; running source shows live SSE values; typecheck passes.

- [x] `UI-134` Fix scenario status reset — preserve runState when navigating away from run view
  Goal: scenario list showed "Not running" immediately after the user navigated away from the run view, even when the scenario was still running on the backend. Root cause: `clearLiveRun` unconditionally reset `runState` to "Not running" on SSE cleanup.
  Fix: `onRunFinished` now updates `scenarios[].runState` to the terminal state ("Stopped" / "Failed" / "Not running"); `clearLiveRun` only removes the `liveRuns` SSE entry without touching `runState`.
  Done when: navigating away from run view while a scenario is running keeps its runState; run-finished SSE event updates runState correctly; typecheck + vitest pass.

- [x] `UI-135` Overview dashboard clarity — separate live sources from active runs; remove Activity nav stub
  Goal: Overview showed "5 sources" badge but an empty list, confusing users. The badge counted SSE-connected data sources while the list showed active run processes (recordings/replays/scenarios) — two unrelated concepts. Activity nav link opened an empty stub page.
  Fix: wrapped sources badge in a clearly-labeled "Live data sources" card with a link to the data sources list; added "Active runs" section header above the run list; improved empty-state copy; removed Activity from top-level nav (route kept).
  Done when: overview clearly distinguishes connected sources from active runs; Activity no longer shows in nav; typecheck passes.

- [x] `UI-130` Align FAULT step params to backend fault model
  Goal: replace the 4 placeholder FE fault kinds (drop/delay/corrupt/quality) with the IS-087 backend contract: BAD_VALUE, MISSING_VALUE, DELAY, CONNECTION_DROP, TIMEOUT, PROTOCOL_ERROR, SOURCE_UNAVAILABLE. Only DELAY has a required param (delayMs); all others are param-free.
  Surface: `Scenario Builder` — fault step editor and config panel.
  Work includes: rewrite `scenario-faults.ts` (FaultKind, FAULT_KIND_LABELS, FAULT_PARAM_SPECS, describeFault); update STEP_FIELD_SPECS.fault kind options in `scenario-steps.ts`; update scenario-faults.test.ts and fault-config-panel.test.tsx.
  Depends: IS-087, IS-088, UI-127.
  Done when: FAULT step kind options match IS-087 contract; params round-trip to backend; typecheck + vitest green.

- [x] `UI-133` Fix data-source frontend shape mismatch — align to IS-127 backend payload
  Goal: the frontend DataSourceResponse still used the old endpoint field removed in IS-127. Fix all layers so the UI shows the simulator serve URL, real device endpoint, source type (basis), and sends the right fields in settings PUT.
  Surface: Data Source Detail — header, overview tab, settings tab, action buttons.
  Work includes: update DataSourceResponse type (simulatorPort/realDeviceEndpoint/serveUrl/basis); update DataSourceRow; update mapDataSource(); update updateSourceConfiguration PUT body; update settings tab to edit realDeviceEndpoint (SCAN only) + async save; update detail header; update overview tab; hide Record button for IMPORT; rename Simulate to Replay recording for IMPORT.
  Depends: IS-127.
  Done when: detail header shows serveUrl; overview shows real device endpoint; source type shown; IMPORT sources show Replay recording; settings save correct; typecheck + vitest green.

- [x] `UI-137` Fix data source settings — Saved badge shown before async save completes
  Goal: `saveChanges` in `DataSourceDetailSettingsTab` was synchronous, so the "Saved" badge appeared before the API call resolved; also the "Unsaved changes" and "Saved" badges could both be visible simultaneously.
  Surface: `Data Source Detail` — Settings tab.
  Work includes: make `saveChanges` async; `await updateSourceConfiguration(...)` before calling `setSavedMessage("Saved")`; 2 behavioral tests added.
  Done when: "Saved" badge only appears after the update call resolves; typecheck + vitest green.

- [x] `UI-459` Wire edit lock to API — schema editor and scenario builder
  Goal: replace the stub `lockedBy` check in the schema editor and the store-only `lockedBy` check in the scenario builder with live advisory edit leases backed by the IS-081 API (POST/DELETE/GET `/edit-lease`).
  Surface: `Data Source Schema Editor`, `Scenario Builder`.
  Work includes: `useEditLease` hook (`frontend/src/shell/use-edit-lease.ts`) — acquires lease on mount, renews every 60 s, releases on unmount (fire-and-forget); lock is ADVISORY (errors fall through to editable mode); schema editor wired to `"data-sources"` lease; scenario builder wired to `"scenarios"` lease; `EditLockBanner` shown when `leaseState === "locked-by-other"`; tests for hook (acquire, renew, release, locked-by-other, error, no-op for empty ids) and for banner integration in both surfaces.
  Depends: IS-081.
  Done when: schema editor acquires lease on open, shows lock banner when locked, releases on close; scenario builder same; typecheck + vitest green.

- [x] `UI-453` Fix real-device-endpoint guard in recording wizard and flow page
  Goal: `captureBlocked` in the recording wizard and `hasRealEndpoint` in the recording flow page were checking `endpoint` (the simulator serve URL) instead of `realDeviceEndpoint` (the actual OPC UA device address). IMPORT/MANUAL sources without a real device were incorrectly unblocked.
  Surface: `Create Recording Wizard`, `Recording Flow Page`.
  Work includes: switch `captureBlocked` and `hasRealEndpoint` to use `realDeviceEndpoint`; update wizard test fixtures to include the field; fix `quality=` assertion in recording-detail test.
  Done when: wizard blocks SCHEMA_AND_DATA when `realDeviceEndpoint` is null; flow page shows no-endpoint panel correctly; vitest green.

- [x] `UI-455` QA bug fixes — schema-only 404 empty state, scenarios Run navigation, IMPORT source actions
  Goal: fix three bugs found during QA: schema-only recording detail shows error on 404 schema fetch (should show empty state); scenarios Run button does not navigate to run view; data sources list shows "Record"/"Simulate" for IMPORT-basis sources.
  Surface: `Recording Detail`, `Scenario Builder`, `Data Sources List`.
  Work includes: recording-detail-page.tsx treat 404 on schema fetch as empty state not error; scenario-builder-page.tsx navigate to /scenarios/:id/run after runScenario; data-sources-list-page.tsx guard Record action and relabel Simulate for IMPORT basis.
  Done when: schema-only recording detail shows "No schema captured." empty state; Run navigates to run view; IMPORT sources show only "Replay recording" action.

- [x] `UI-457` Pin/unpin parameters in Values tab
  Goal: let users pin individual parameters in the live Values tab so they stay visible at the top through SSE snapshots, and the existing "Pinned only / Unpinned only" filter works against real user state.
  Surface: `Data Source Detail — Values tab`.
  Work includes: maintain a `Set<string>` of pinned nodeIds in component state (survives snapshots); add a small pin toggle button (icon-only) to each row in the Values table; apply pinned state when building rows from SSE events; show pinned rows sorted to top or marked distinctly.
  Depends: none.
  Done when: clicking pin on a row marks it Pinned; SSE snapshot preserves pinned state; Pinned-only filter shows only pinned rows; typecheck + vitest green.

- [x] `UI-458` Add SCAN schema review step to Create Data Source wizard
  Goal: insert a new "Scan" wizard step (between Setup and Schedule) that auto-starts a schema scan via the backend scan API, polls until complete, shows discovered node count, surfaces unknown-type resolution selects, and blocks Next until scan is complete and all unknowns resolved.
  Surface: `Create Data Source Wizard`.
  Work includes: add `DiscoveredNodeResponse`, `TypeResolutionEntry` exported types; add `scanStepValidationMessage` exported function; insert `{ id: "scan", label: "Scan" }` into `SCAN_STEPS`; add scan state vars (`scanJobId`, `scanStatus`, `scanTrigger`, `scanResult`, `typeResolutions`, `scanErrorMessage`); auto-start `useEffect` with `setInterval` polling (deps: `[activeStepId, scanTrigger]`); `renderScanStep()` with scanning/complete/error/partial views; unknown-type resolution selects with `handleTypeResolutionChange`; `retryScan()` incrementing `scanTrigger`; on Review Create call `scan/{jobId}/create` for scan basis; 8 integration tests + 6 unit tests for validation function.
  Done when: scan step appears in wizard; auto-scan starts on entry; polling updates UI; unknown types can be resolved; Next blocked until ready; Create calls scan create endpoint; typecheck + vitest green.

- [x] `UI-139` QA bug fixes — data source loading, null-safe filters, quality param mismatch, evidence crash
  Goal: fix five bugs found during QA pass on master: wizard/list missing loadDataSources call; capturedBy/owner/sourceIds null crashes in filters; quality filter param name mismatch with backend.
  Surface: `Create Recording Wizard`, `Recordings List`, `Evidence List`, `Recording Detail`, `Scenarios List`.
  Work includes: add `loadDataSources` on mount in wizard and recordings-page; null-guard `capturedBy`, `sourceIds`, `owner`; fix query param `qualities` → `quality` in `buildValuesQs`; fix type declarations to reflect nullable API fields.
  Done when: vitest green; quality checkbox filter excludes correct values; source names appear in recordings list; evidence page renders without crash.

- [x] `UI-138` Fix recording/replay UX bugs — schema field names, idle label, 409 compat ack, no-endpoint guard
  Goal: address several UX regressions in the recording and replay flows.
  Surface: `Recording Detail`, `Recording Flow`, `Replay Flow`.
  Work includes: fix field name display in recording detail (schema-based names); fix "Idle" label on replay when source has no real endpoint; handle 409 schema-mismatch response in replay — show "Run anyway" confirmation panel with `compatibilityAck=true` retry; disable "Start recording" button and show panel when source has no real device endpoint.
  Done when: 409 → schema-mismatch → "Run anyway" retry flow tested; `!hasRealEndpoint` disabled path tested; typecheck + vitest green.

- [x] `UI-132` Recording wizard — redirect SCHEMA_AND_DATA to live capture flow
  Goal: when user selects "Schema + data" scan type in the recording wizard and clicks "Start capture", navigate to RecordingFlowPage (live capture flow) instead of calling POST /recordings. SCHEMA_ONLY keeps the existing shell-creation path. Adds endpoint guard and contextual explanation panels.
  Surface: `Create Recording Wizard`.
  Work includes: `createRecording()` in `create-recording-wizard-page.tsx` branches on scanType; live-capture warning added to scan-type step; capture-blocked warning added to review step; button label changes to "Start capture" for SCHEMA_AND_DATA.
  Depends: UI-115 (recording flow page exists), IS-045 (POST .../recording/start).
  Done when: SCHEMA_AND_DATA → navigates to /data-sources/:id/recording; SCHEMA_ONLY → POST /recordings as before; typecheck + vitest green.

- [x] `UI-131` Recording name field — wizard input, store mapping, list + detail display
  Goal: surface the optional `name` field (from IS-144) end-to-end in the frontend.
  Surface: `Create Recording Wizard`, `Recordings list`, `Recording Detail`
  Work includes: add `name?: string` to `ReusableArtifact`; add `name` to `RecordingResponse` and `mapRecording()` in `artifacts-store.ts`; add name text input to wizard review step; pass trimmed name in POST body; show name as primary label in recordings list (with source as subtitle when both present); include name in list search; show name in detail page header (with id as secondary).
  Depends: IS-144.
  Done when: wizard sends name; list shows name as primary label; detail page header shows name; typecheck + vitest green.

- [x] `UI-129` Wire scenario run/stop to backend API + live SSE run view
  Goal: replace mock-backed run view with live SSE data; wire run/stop actions to real backend.
  Surface: `Scenarios`, `Scenario Run View`
  Work includes: `stopScenarioRun` in scenarios-api; `LiveRunState` + `liveRuns` + `clearLiveRun` in scenarios-store; `runScenario` stores runId/evidenceId; `stopScenario` calls stop API; run view subscribes to SSE step-started/step-completed/run-finished events via EventSource; scenario-run.ts mock removed.
  Depends: IS-141.
  Done when: Run navigates to live SSE view; stop calls API; step timeline updates from SSE; TypeScript build + vitest pass.

- [x] `UI-120` Integrate the regrouped API (9 groups)
  Goal: reflect the backend's 9-group API tags (IS-135) on the frontend. No FE code
  change required — the client (`src/api/client.ts`) calls endpoints by raw path and
  does not consume OpenAPI tags, which are documentation-only metadata (no path,
  schema, or behavior change in IS-135). Closed as documentation alignment.
  Depends: IS-135.
- [x] `UI-118` Fix data source detail, schema editor, and create recording wizard bugs
- [x] `UI-117` Fix project selection persistence — lost on refresh, not shown on direct URL
- [x] `UI-116` Sample import flow + local-mode auth skip in wizard
- [x] `UI-115` Data-source wizard + recordings rework
  Goal: (1) rework the Create Data Source wizard — rename "Scan real source" → "Real source", remove pre-start schema-scan step, add schedule (start/end datetime) step, replace simulatored endpoint with simulatorPort input, change "Prepared data" import step from samples to recordings; (2) replace the Recordings & Samples page — remove Samples block, add Create Recording wizard (new connection → scan type → schedule → review) with file import support, add Recording detail page (Schema + Values tabs); (3) in the "Prepared data" wizard branch show recordings list instead of samples.
  Surface: `Create Data Source Wizard`, `Recordings & Samples` (now Recordings), new `Create Recording Wizard`, new `Recording Detail`.
  Work includes: wizard step sequence update (schema step removed, schedule step added); simulatorPort number input pre-filled per protocol (4840/502); realDeviceEndpoint only for scan basis; import step shows recordings; SamplesSection removed from recordings page; CreateRecordingWizardPage with steps: connection, scan type (schema-only vs schema+data), schedule; RecordingDetailPage with Schema and Values tabs; import recording file dialog kept; routes /recordings/new and /recordings/:id added.
  Depends: IS-127 (backend payload).
  Done when: wizard sends simulatorPort + realDeviceEndpoint; schedule step present; import shows recordings; recordings page has no samples block; create recording flow works; detail page shows schema/values; typecheck + vitest pass.

- [x] `UI-114` UX polish — wizard rework, recordings, scenarios, schema editor fixes

- [x] `UI-113` Fix source detail crash on direct navigation — hooks ordering + load-on-mount
  Goal: `DataSourceDetailPreviewPage` crashed with a Rules of Hooks violation when navigated to directly (e.g. bookmark or refresh) because `useMemo` was called after an early `if (!source) return`. Also, the page showed a permanent "source not found" error on direct URL because the store was empty and nothing triggered a fetch.
  Surface: `Data Source Detail` — all tabs.
  Work includes: move `useMemo(stopConfirmationModel)` before the early return; add `useEffect` to call `loadDataSources` when store is empty on mount; use `fetchedForProjectRef` (tracks project id, not a bare boolean) to prevent infinite re-fetch; show loading spinner while fetch is in flight; 8 tests covering all states.
  Done when: direct navigation to `/data-sources/<id>` loads correctly; invalid id shows error panel (no infinite spinner); all 8 tests pass.

- [x] `UI-456` Parameter count in data source list and detail header
  Goal: display the real VARIABLE node count (delivered by IS-149) in the data sources list row and the detail header metadata band.
  Surface: `Data Sources List`, `Data Source Detail` — header.
  Work includes: add `parameterCount?: number` to `DataSourceResponse` in `data-sources-store.ts`; map `d.parameterCount ?? 0` in `mapDataSource`; list row and detail header already call `parameterCount.toLocaleString()`; add tests: store maps the field; list renders the value.
  Depends: IS-149.
  Done when: list row shows real VARIABLE node count from API; detail header shows same value; typecheck + vitest green.

- [x] `UI-460` Wire admin users page to IS-118 API
  Goal: replace mock data and save stubs in AdminUsersPage with real API calls to IS-118 endpoints.
  Surface: `Admin Users`
  Work includes: `useEffect` fetching `GET /api/v1/admin/users` on mount with loading/error states; `handleRoleChange` → `PATCH .../roles`; `handleDeactivate/handleActivate` → `PATCH .../status`; map `AdminUserApiResponse` → `UserRow` (displayName→name, subject→email, ACTIVE/SUSPENDED→active/inactive); remove mock imports; add tests for loading panel, GET-failure error panel.
  Depends: IS-118.
  Done when: admin users page loads from API; role and status changes call API; loading and error states render correctly; typecheck + vitest green.

- [x] `UI-461` Wire recording import/export to IS-070 API
  Goal: replace mock RecordingImportDialog with real multipart upload; add Export button to recording detail page.
  Surface: `Recordings`, `Recording Detail`
  Work includes: `RecordingImportDialog` → `POST .../recordings/import` (FormData); on success reload artifacts store; Export button → `POST .../export` streams ZIP download; fix `apiFetch` to not set Content-Type for FormData bodies.
  Depends: IS-070.
  Done when: import uploads real ZIP and reloads list; error state shown on API failure; export button downloads `.iotsim` bundle; typecheck + vitest green.

- [x] `UI-462` Simplify recording wizard + add capture step to data source scan flow
  Goal: recordings are always live capture — remove scan-type step from Create Recording wizard; add a Recording step to the Create Data Source wizard (scan basis) so users can choose to start live capture immediately after creation.
  Surface: `Create Recording`, `Create Data Source`
  Work includes: `create-recording-wizard-page.tsx` — remove scan-type step, always navigate to live capture, sources without realDeviceEndpoint shown as disabled; `create-data-source-wizard-page.tsx` — add `recording` step to SCAN_STEPS (scan→recording→schedule→review), two options: Start live capture / Skip; on submit with startCapture=true, redirect to `/data-sources/{id}/recording`.
  Done when: recording wizard has 2 steps (source + review); data source scan flow has 7 steps with recording choice; startCapture=true redirects to live capture; typecheck + vitest green.

- [x] `UI-463` Hide Modbus TCP from UI until IS-059 is implemented
  Goal: remove Modbus TCP from the Create Data Source wizard protocol step and from the Data Sources list page protocol filter — IS-059 (Modbus TCP worker) is not yet implemented, so showing the option lets users create sources that will never work.
  Surface: `Create Data Source`, `Data Sources`
  Work includes: remove `{ label: "Modbus TCP", value: "Modbus TCP" }` from `protocolOptions` in `create-data-source-wizard-page.tsx` and from the protocol filter in `data-sources-list-page.tsx`; all Modbus-specific form logic preserved — re-enable by restoring entries when IS-059 lands.
  Depends: IS-059 (to re-enable).
  Done when: protocol step shows only OPC UA; list filter shows only All protocols / OPC UA; typecheck + vitest green.

- [x] `UI-466` IMPORT source creation — copy schema from recording
  Goal: when creating an IMPORT data source the wizard fetches the selected recording's schema and passes it as `initialSchema` so the source has variables immediately after creation instead of showing an empty schema tab.
  Surface: `Create Data Source Wizard`
  Work includes: in the IMPORT create path, call `GET /recordings/{id}/schema` before `POST /data-sources`; include `nodes` as `initialSchema` in the create body; failure to fetch is non-fatal (source is created without schema).
  Depends: IS-150 (recording name in manifest — unrelated but same release).
  Done when: IMPORT source created from a named recording shows the correct schema and parameterCount > 0 immediately on the detail page; typecheck green.

- [x] `UI-465` Recording start UX — no-schema guard and detailed error messages
  Goal: surface actionable errors when recording cannot start due to missing schema; replace raw "Bad Request" toast with a clear title + backend detail message.
  Surface: `Recording Flow`
  Work includes: disable "Start recording" button when parameterCount === 0; show inline SharedStatePanel explaining why (SCAN: recreate source; MANUAL: add variables in Schema tab); surface ApiError.detail as toast message field so backend reason is visible.
  Depends: none.
  Done when: button disabled when schema empty; panel shown with actionable message; toast includes error detail; typecheck green.

- [x] `UI-464` Evidence UX — hide idle badge, add navigation links
  Goal: fix two UX gaps in evidence surfaces: (1) replay-flow-page shows "EVIDENCE: READY" badge before any replay starts; (2) evidence-detail-page displays sourceIds, recordingId, scenarioId as raw text with no navigation.
  Surface: `Replay Flow`, `Evidence Detail`
  Work includes: hide evidence StatusBadge when replayState is idle in replay-flow-page.tsx; add DetailLinkRow component to evidence-detail-page.tsx; sourceIds link to /data-sources/:id; recordingId links to /recordings/:id; scenarioId links to /scenarios/:id.
  Depends: none.
  Done when: badge absent on initial replay page load; tapping an ID navigates to the correct surface; typecheck green.

- [x] `UI-467` ✅ Recordings — delete action
  Goal: let users delete a recording from the Recordings list, mirroring the existing Data Sources delete pattern (confirmation dialog + surfaced backend dependency error).
  Surface: `Recordings`
  Work includes: add `deleteRecording` to `artifacts-store.ts` calling `DELETE /api/v1/projects/{pid}/recordings/{id}` (mirrors `deleteSample`/`deleteDataSource`); add a delete button per row in `recordings-page.tsx` with a `ConfirmationDialog` (danger tone); on 422 `RetentionDependencyException` (recording referenced by a scenario REPLAY step or an active/queued run), surface `ApiError.detail` as the error toast so the reason is visible.
  Depends: IS-092 (backend delete + dependency check, already shipped).
  Done when: deleting an unreferenced recording removes it from the list and calls the DELETE endpoint; deleting a referenced recording shows the backend's dependency error instead of silently failing; typecheck + vitest green.

- [x] `UI-468` ✅ Recordings — fix origin label mislabeling
  Goal: the "Imported" badge must only appear on recordings actually created via file import; recordings created via live capture must show "Recorded".
  Surface: `Recordings`, `Recording Flow`
  Work includes: flip `mapRecording`'s origin mapping in `artifacts-store.ts` from `r.origin === "SCAN_RECORD" ? "captured" : "imported"` (unsafe default: any unexpected value fell to "imported") to `r.origin === "IMPORTED" ? "imported" : "captured"`; `recording-flow-page.tsx`'s post-stop `appendRecording` call now sets `origin: "captured"` explicitly instead of omitting it.
  Depends: none.
  Done when: a live-captured recording shows "Recorded" immediately after saving and after a reload; only recordings with backend `origin: IMPORTED` show "Imported"; typecheck + vitest green.

- [x] `UI-469` ✅ Create Data Source wizard — make Stop Scan more prominent; block Back/Cancel while scanning
  Goal: the Stop Scan button (IS-164) reads as a routine action today; make it visually distinct as a stop/danger action. Also, Back and Cancel are currently clickable while a scan is in flight, letting the user navigate away from an orphaned running scan without stopping it first.
  Surface: `Create Data Source Wizard` (scan step)
  Work includes: apply `shell-action-danger` (existing danger button class) to the Stop Scan button in `create-data-source-wizard-page.tsx`; disable the footer's Back and Cancel buttons while `scanStatus === "scanning"`.
  Depends: IS-164.
  Done when: Stop Scan renders as a danger-styled button; Back and Cancel are disabled for the duration of an active scan and re-enable once it completes, fails, or is stopped; typecheck + vitest green.

- [x] `UI-470` ✅ Create Data Source wizard — paginate + virtualize scanned node list
  Goal: a scan against a huge address space returns thousands of nodes; today `renderScanStep` maps every unknown-type node straight into the DOM (`<li>` + two `<select>` each) with no pagination or virtualization, which would choke the browser once IS-165 removes the backend's node-count ceiling.
  Surface: `Create Data Source Wizard` (scan step)
  Work includes: added `@tanstack/react-virtual` dependency (STACK.md updated); on terminal scan status, `fetchAllScanNodes()` pages nodes from the `GET .../scan/{jobId}/nodes?cursor=&limit=` endpoint (IS-165) into one array (the job-status response's full `nodes` list is still returned for back-compat but no longer read); new `UnknownNodesList` component renders the unknown-typed subset via `useVirtualizer` so only visible rows hit the DOM regardless of total count; a `scanHandlingTerminalRef` guards against overlapping poll ticks double-starting the fetch, and a `scanStoppedRef` guards against Stop Scan racing the fetch; `unknownNodes`/`typeResolutionsByNodeId` are memoized (the latter as a `Map` for O(1) per-row lookup) instead of recomputed on every render.
  Depends: IS-165.
  Done when: scanning a source with thousands of unknown-type nodes renders smoothly (no unbounded DOM growth) and all nodes are still selectable/resolvable; typecheck + vitest + build green.

- [x] `UI-471` ✅ Create Data Source wizard — scan re-triggers on Back/Next instead of reusing result
  Goal: navigating Next away from the scan step and then Back re-triggers a brand-new scan job against the device instead of reusing the already-completed scan result.
  Surface: `Create Data Source Wizard` (scan step)
  Work includes: the scan auto-start `useEffect` in `create-data-source-wizard-page.tsx` no longer resets `scanStatus`/`scanResult`/`scanJobId` in its cleanup (which ran on every step change, defeating the `scanStatus !== "idle"` guard); cleanup now only stops the in-flight poll interval; re-entering the scan step while a scan is still in-flight (status `"scanning"`) resumes polling the existing job (via `scanJobIdForCreateRef`) instead of starting a second one; explicit rescans still go through `retryScan()`/`stopScan()`, which reset state themselves.
  Depends: none.
  Done when: completing a scan, clicking Next, then Back shows the previously discovered result with no new scan job; leaving mid-scan and returning resumes polling the same job; Retry/Stop Scan unaffected; typecheck + vitest green.

- [x] `UI-472` ✅ Create Data Source wizard (Real source) — "This source could not be found" after Create source
  Goal: creating a Real source (scan-based) data source showed a permanent "This source could not be found." error on the destination page (record/replay) even though the source was created successfully on the backend.
  Surface: `Create Data Source Wizard`, `Recording Flow`, `Replay Flow`
  Work includes: `createSource()`'s scan-based and import/manual paths in `create-data-source-wizard-page.tsx` now call `loadDataSources(currentProjectId)` before navigating away, so the store holds the freshly created row (mirrors what the synthetic path already did via the store's own `createSyntheticSource` action); `recording-flow-page.tsx` and `replay-flow-page.tsx` also gained a self-healing effect (mirroring `data-source-detail-preview-page.tsx`'s existing pattern) that reloads data sources once if the source isn't found locally yet, as defense-in-depth for any other route that lands on these pages before the store is warm.
  Depends: none.
  Done when: creating a Real source with "start capture" enabled lands on the live capture page showing the new source instead of "not found"; same for the non-capture and IMPORT paths; typecheck + vitest green.

- [x] `UI-473` ✅ Create Data Source wizard — scan reuses stale result after endpoint changed on Setup
  Goal: regression found via live testing right after UI-471: completing a scan, going back to Setup, changing the real device endpoint, and returning to Scan reused the stale result (or in-flight job) for the OLD endpoint instead of scanning the new one.
  Surface: `Create Data Source Wizard` (scan step)
  Work includes: the scan auto-start `useEffect` in `create-data-source-wizard-page.tsx` now tracks a `scannedParamsKeyRef` (protocol + endpoint) alongside `scanStatus`; the "already scanned, don't re-scan" guard from UI-471 only applies when the key matches the current form values — if the endpoint/protocol changed since the last scan, a fresh scan always runs (clearing stale `scanResult`/`scanJobId`/`typeResolutions`/`scanErrorMessage` first, and cancelling any stale in-flight poll for the old params).
  Depends: UI-471.
  Done when: completing a scan, going back to Setup, changing the endpoint, and returning to Scan always runs a fresh scan against the new endpoint; the UI-471 same-endpoint no-re-scan behavior still holds; typecheck + vitest green.

- [x] `UI-474` ✅ Data Source Detail — long endpoint URLs overflow their cards, blow out page width
  Goal: long unbroken endpoint URLs (e.g. a long OPC UA `opc.tcp://...` address) overflowed their card/field boundaries on the Data Source Detail page instead of wrapping, pushing the whole page wider than the viewport (tabs and side panels cut off at both edges).
  Surface: `Data Source Detail` (Overview tab, Settings tab, page header)
  Work includes: added `break-all` to the "Simulator serve URL" and "Real device endpoint" `<dd>` values in `data-source-detail-overview-tab.tsx`, the read-only "Simulator serve URL" field in `data-source-detail-settings-tab.tsx`, and the "Real device:" header line in `data-source-detail-preview-page.tsx`, so long unbreakable URLs wrap within their container instead of forcing horizontal page overflow.
  Depends: none.
  Done when: the Overview tab, Settings tab, and page header wrap long endpoint URLs onto multiple lines and the page never scrolls horizontally regardless of endpoint length; verified live against a real long OPC UA demo endpoint; typecheck + vitest green.

- [x] `UI-475` ✅ Recordings — Delete action should be a text link, matching Data Sources style
  Goal: the Recordings list's Delete action rendered as a solid red button (`shell-action-danger`), while the Data Sources list's Delete renders as a red text link (`shell-text-action-danger`) via the shared table-pattern row actions. Align the two for visual consistency.
  Surface: `Recordings`
  Work includes: changed the Delete button's className in `recordings-page.tsx` from `shell-action-danger` to `shell-text-action-danger`; unchanged aria-label, click-stopPropagation, and confirmation-dialog flow.
  Depends: none.
  Done when: Recordings' Delete renders as a red text link matching Data Sources; typecheck + vitest green.

- [x] `UI-476` ✅ Recording Flow — query capture status on mount instead of always assuming ready
  Goal: `RecordingFlowPage`'s `recordingState` was pure local React state that always started at `"ready"` on mount, regardless of whether a capture was actually already running for the data source server-side — a capture left running (e.g. after a page reload with no Stop) was invisible and unstoppable from the UI, only surfacing as a rejected Start (IS-166 added the status endpoint for exactly this reason).
  Surface: `Recording Flow`
  Work includes: `recording-flow-page.tsx` now calls `GET .../data-sources/{id}/recording/status` once per project+source in a mount effect (guarded by a ref so it doesn't refire on re-render); if `capturing: true`, initializes into the active (`no-values-yet`) state with `activeRecordingId` set from the response instead of "ready", so Stop recording is available and reflects the true server-side state.
  Depends: IS-166.
  Done when: opening the recording page for a source with an active server-side capture shows Stop recording (not Start) immediately; a source with no active capture behaves as before; typecheck + vitest green.

- [x] `UI-477` ✅ Create Data Source wizard — Schedule step: replace native datetime-local with a locale-fixed date-picker
  Goal: the Schedule step's start/end fields used native `<input type="datetime-local">`; the app's own text is English-only, but the native picker's calendar popup renders per the viewer's OS/browser locale (e.g. Russian) with no way for the page to override it.
  Surface: `Create Data Source Wizard` (schedule step)
  Work includes: new `ScheduleDatePicker` (`frontend/src/ui/schedule-date-picker.tsx`) wraps `react-datepicker` with an explicit registered `en-US` locale (`date-fns/locale/en-US`) and `showTimeSelect`, so the calendar always renders in English regardless of OS/browser locale; parses/formats to the same `YYYY-MM-DDTHH:mm` string the form state already used, so no other wizard code changed; `renderScheduleStep()`'s Start-at/End-at fields now use it. New dependency `react-datepicker` (STACK.md updated, mirrors how UI-470 added `@tanstack/react-virtual`).
  Depends: none.
  Done when: Schedule start/end fields render an English-locale calendar+time picker regardless of the viewer's OS locale; existing schedule validation/review-step display unaffected; typecheck + vitest green.

- [x] `UI-478` ✅ Create Data Source wizard (Synthetic) — move step navigation to the top for long schemas
  Goal: on the Synthetic basis's "Configure profile" step, `SyntheticProfileStep` maps an unbounded list of variable/measurement rows, pushing the wizard's single Back/Next/Create-source footer far below the fold for large generated schemas and forcing users to scroll to advance.
  Surface: `Create Data Source Wizard`
  Work includes: refactored the footer's Back/Next/Create-source button group in `create-data-source-wizard-page.tsx` into a shared `renderStepNav(position)` function, rendered once right under the step header (before `renderCurrentStep()`) and once at the bottom in its original footer position, so both copies always share the same handlers and disabled-state logic; Cancel stays bottom-only (unchanged position/behavior).
  Depends: none.
  Done when: a top copy of Back/Next/Create-source renders above every step's content, in sync with the bottom copy; clicking the top Next/Back/Create-source behaves identically to the bottom one; typecheck + vitest green.

- [x] `UI-479` ✅ Create Data Source wizard (Synthetic) — surface backend error detail on Create source failure
  Goal: creating a Synthetic data source that fails on the backend (e.g. 400 Bad Request — invalid pattern config, port conflict) surfaced only a generic toast ("Failed to create synthetic source"), silently dropping the backend's specific `ApiError.detail` validation message.
  Surface: `Create Data Source Wizard` (synthetic basis)
  Work includes: all three `catch` blocks in `createSource()` (`create-data-source-wizard-page.tsx`) — synthetic, scan-path, and manual/import-path, which shared the identical bug — now compute `message` via `err instanceof ApiError ? (err.detail ?? err.message) : (err instanceof Error ? err.message : undefined)` and pass it to `push({ tone: "error", title, message })`, mirroring the existing pattern in `recording-flow-page.tsx`'s `handleStartRecording()` and `recordings-page.tsx`'s delete handling.
  Depends: none.
  Done when: a rejected `createSyntheticSource(...)` call surfaces the backend's `.detail` as the toast message instead of a generic title-only toast; typecheck + vitest green.

- [x] `UI-480` ✅ Create Data Source wizard — top step nav (UI-478) shows on every step instead of only the long Synthetic configure step
  Goal: UI-478's top copy of the Back/Next/Create-source nav was supposed to render only on the Synthetic basis's "Configure profile" step (the one with an unbounded generated-variable list), but the shipped implementation rendered it unconditionally on every wizard step — Protocol, Basis, Setup, Scan, Review — cluttering short steps with a redundant duplicate nav bar both above and below their content.
  Surface: `Create Data Source Wizard`
  Work includes: gated the top `renderStepNav("top")` block in `create-data-source-wizard-page.tsx` behind `activeStepId === "configure"`, so it renders only on the Synthetic basis's "Configure profile" step; the bottom nav is unchanged and still renders on every step. Updated the UI-478 test suite's third test (`create-data-source-wizard-page.test.tsx`) to exercise the synthetic "configure" step's top Next button (previously it incorrectly exercised the "setup" step, which no longer renders a top nav) and assert it advances to the "Review" step.
  Depends: UI-478 (merged).
  Done when: the top nav renders only on the Synthetic "Configure profile" step and nowhere else; the bottom nav and Cancel are unaffected; typecheck + vitest green.

- [x] `UI-481` ✅ Create Data Source wizard — Import picker shows source name, not truncated id, when a recording has no name
  Goal: the Import basis recording picker showed unnamed recordings as `Recording <truncated-id>`, less useful than the Recordings list's fallback (the recording's originating data source name) for the same unnamed recordings.
  Surface: `Create Data Source Wizard` (import basis)
  Work includes: the Import step's recording list in `create-data-source-wizard-page.tsx` now resolves `sourceName` from the already-loaded `dataSources` store by the recording's `sourceId` and falls back `name || sourceName || \`Recording ${id.slice(0,8)}\`` — matching `recordings-page.tsx`'s existing fallback chain.
  Depends: none.
  Done when: an unnamed recording's picker row shows its originating source's name instead of a truncated id, when that source is resolvable; typecheck + vitest green.

- [x] `UI-482` ✅ Create Data Source wizard (Synthetic) — restrict pattern/value input for CONSTANT-only structural types
  Goal: IS-168 (backend) restricted structural/identifier data types (GUID, STATUS_CODE, QUALIFIED_NAME, NODE_ID, EXPANDED_NODE_ID, XML_ELEMENT, BYTES, DATETIME) to a CONSTANT pattern only, since a dynamic pattern has no physical meaning for them; the wizard's "Configure profile" step still offered all 6 pattern choices and a numeric-only Value input for every node, so picking e.g. SINE for a NODE_ID node still failed with a 400 with no indication in the UI of why.
  Surface: `Create Data Source Wizard` (synthetic basis)
  Work includes: `synthetic-profile-step.tsx` now locks the Pattern select to a disabled "Constant (required)" option for `CONSTANT_ONLY_TYPES` nodes; the CONSTANT Value input switches to a text input (with a hex placeholder for BYTES) for string-shaped structural types and BYTES, numeric otherwise; `defaultDraft(dataType)` seeds a type-appropriate default (UUID for GUID, `ns=0;i=0` for NODE_ID/EXPANDED_NODE_ID, `0` for STATUS_CODE, current timestamp for DATETIME, empty string otherwise) instead of always defaulting to SINE; `toPattern`/`draftFromPattern` round-trip the new `stringValue`/`bytesValueBase64` wire fields (hex ⇄ Base64 for BYTES) alongside the existing numeric `value`. `SyntheticPatternSpec` in `data-sources-store.ts` widened to match.
  Depends: IS-168 (backend, merged).
  Done when: a structural/identifier node's Pattern select is locked to Constant with a sensible default value; creating a Synthetic source reusing a schema with a NODE_ID (or other structural type) node succeeds end-to-end; typecheck + vitest + build green.

- [x] `UI-483` ✅ Create Data Source wizard (Synthetic) — simplify Pattern choice with plain-language labels + progressive disclosure
  Goal: the per-measurement Pattern select showed all 6 pattern types flat and unconditionally, using signal-processing jargon ("Sine wave", "Random walk" vs "Random (uniform)") a non-technical user can't map to what the generated data will look like.
  Surface: `Create Data Source Wizard` (synthetic basis)
  Work includes: `synthetic-profile-step.tsx` splits `PATTERN_TYPES` into `SIMPLE_PATTERN_TYPES` (Fixed value / Smooth (rises & falls) / Random — CONSTANT/SINE/RANDOM_UNIFORM, shown by default) and `ADVANCED_PATTERN_TYPES` (Rising ramp (resets) / Alternating (on/off) / Random (drifting) — RAMP/SQUARE/RANDOM_WALK, hidden behind a new "Show more patterns" button per row); a row auto-expands if its current pattern is already one of the advanced three (e.g. loaded from a saved config or a recording-derived suggestion). No change to the underlying pattern set, serialization, or the CONSTANT-only lock (UI-482).
  Depends: none.
  Done when: the Pattern select shows only the 3 plain-language options by default; "Show more patterns" reveals all 6; a row with an already-selected advanced pattern starts expanded; typecheck + vitest + build green.

- [x] `UI-484` ✅ Synthetic wizard — clarify Prefill from recording (purpose text + mark which measurements changed)
  Goal: "Prefill from recording" (IS-146) works correctly, but a schema can have far more measurements than a recording actually captured (e.g. 250 vs. 10 matched); after a successful prefill the toast says "Prefilled N measurements" but nothing in a long list shows which rows changed, so a user scrolling to an unmatched row could reasonably conclude prefill did nothing.
  Surface: `Create Data Source Wizard` (synthetic basis)
  Work includes: a plain-language explanatory line added under the Prefill control; `prefillFromRecording` now tracks which nodeIds were actually updated (computed from the `drafts` closure, not the `setDrafts` updater's `cur` — reading that synchronously right after the call would be stale) into a new `prefilledNodeIds` state, rendered as a "Prefilled" badge + accent border on each affected row, with the view auto-scrolling the first affected row into view via a `rowRefs` map. No change to the underlying prefill matching/derivation logic.
  Depends: none.
  Done when: the Prefill control explains its purpose; every row actually updated by the last prefill is visually marked; the view scrolls to the first affected row after a successful prefill; typecheck + vitest + build green.

- [x] `UI-485` ✅ Import Data wizard recording picker — show time, not just date
  Goal: the Import Data wizard's recording picker formatted `createdAt` via `toLocaleDateString` (date only), unlike the Recordings page (date+time via `toLocaleString`); combined with recordings that share a fallback name (any recording created before IS-169, or an unnamed import), multiple recordings from the same source on the same day were indistinguishable in this picker.
  Surface: `Create Data Source Wizard` (import basis)
  Work includes: `create-data-source-wizard-page.tsx`'s recording picker now formats `createdAt` with `toLocaleString` including `hour`/`minute`/`hour12: false`, matching `recordings-page.tsx`'s `formatDate`.
  Depends: none (complements IS-169, backend, merged).
  Done when: the picker's date/time subtitle includes a time component matching the Recordings page's format; typecheck + vitest + build green.

- [x] `UI-486` ✅ Allow decimal values in synthetic pattern number fields
  Goal: the synthetic pattern editor's numeric fields (Min, Max, Period, Volatility, Update rate, Seed) rejected decimal input — the backend already stores these as `Double` (`PatternSpec`), but the `<input type="number">` elements had no `step` attribute, so the browser's default integer-only step semantics applied.
  Surface: `Create Data Source Wizard` (synthetic basis)
  Work includes: `synthetic-profile-step.tsx` — added `step="any"` to every numeric pattern field (Seed, Min, Max, Period, Volatility, Update rate). No backend or parsing change needed; `num()` already accepts decimals via `Number(s)`.
  Depends: none.
  Done when: every numeric pattern field accepts a decimal value (e.g. 36.6); typecheck + vitest + build green.

- [x] `UI-487` ✅ Select-all / deselect-all for synthetic measurement rows
  Goal: the synthetic pattern editor's measurement list has a per-row "enabled" checkbox but no way to select/deselect all rows at once — with schemas of 200+ measurements, toggling each row individually is impractical.
  Surface: `Create Data Source Wizard` (synthetic basis)
  Work includes: `synthetic-profile-step.tsx` gained a master "Select all" checkbox above the measurement list (`setAllEnabled`, `enabledCount`/`allEnabled`/`someEnabled` derived from `drafts`), with an indeterminate visual state when some but not all rows are enabled.
  Depends: none.
  Done when: the master checkbox checks/unchecks every row at once and shows indeterminate when partially selected; typecheck + vitest + build green.

- [x] `UI-488` ✅ Bulk-apply a pattern to selected synthetic measurement rows
  Goal: there was no way to set the same pattern (e.g. Random) for many synthetic measurement rows at once — the user had to change each row's Pattern select individually, even after selecting multiple rows via UI-487's select-all.
  Surface: `Create Data Source Wizard` (synthetic basis)
  Work includes: `synthetic-profile-step.tsx` gained a "Set pattern for selected…" control next to UI-487's select-all checkbox; `setPatternForSelected` applies the chosen pattern to every currently-enabled row, skipping `CONSTANT_ONLY_TYPES` (IS-168) nodes, reusing `changePattern`'s per-node recording-suggestion re-apply logic.
  Depends: UI-487.
  Done when: the bulk control applies a pattern to every enabled row and leaves deselected rows untouched; typecheck + vitest + build green.

- [x] `UI-489` ✅ Manual Schemas list page (nav/list)
  Goal: a new "Manual Schemas" section (project-scoped) listing reusable, standalone structure artifacts — name, protocol, variable count, updated date — with create/duplicate/delete actions, modeled on `recordings-page.tsx`.
  Surface: new `Manual Schemas` list page + nav entry.
  Depends: IS-171, IS-172 (backend, merged).
  Done when: list loads/paginates from `GET /manual-schemas`; create/duplicate/delete wired; empty and loading states; typecheck + vitest + build green.

- [x] `UI-490` ✅ Standalone Manual Schema editor with Save / Save As
  Goal: reuse the existing Full Schema Editor (`data-source-schema-editor.tsx`) in a standalone mode bound to a `ManualSchema` instead of a data source's schema. On Save with unsaved changes, prompt the user to choose: save into this schema, or save as a new one (no monotonic version chain, per `03_DOMAIN_MODEL.md` §ManualSchema).
  Surface: `Manual Schemas` — editor.
  Depends: UI-489, IS-172 (merged).
  Done when: structural edits at any depth, types, unit/description all work standalone; Save/Save-As dialog appears only when there are unsaved changes; typecheck + vitest + build green.

- [x] `UI-491` ✅ Synthetic wizard — pick a Manual Schema as parameter source
  Goal: in the Create Data Source wizard's Synthetic "Configure profile" step (`synthetic-profile-step.tsx`), add "Manual schema" as a parameter-source option alongside the existing "existing source's schema" and "prefill from recording" options — a schema is required to get any parameters to drive.
  Surface: `Create Data Source Wizard` (synthetic basis).
  Depends: IS-173 (merged), UI-489.
  Done when: picking a Manual Schema populates the per-measurement pattern editor from its nodes; `manualSchemaId` sent to `POST .../data-sources/synthetic`; mutually exclusive with picking an existing source; typecheck + vitest + build green.

- [x] `UI-492` ✅ Manual Schema — OPC UA node library and multi-node structure editor
  Goal: let users compose a reusable OPC UA server address-space structure, rather than manually adding one node at a time. The editor must offer documented node structures, complete supported type selection, explicit placement under any folder, and batch insertion of typed variables. It authors schema only; it does not model OPC UA Alarm & Condition runtime behaviour.
  Surface: `Manual Schemas` — editor.
  Depends: UI-490; runtime hierarchy materialization in IS-175.
  Done when: editor labels are English; users can add multiple sibling folders/variables under an explicitly chosen parent; batch input adds valid `Name, TYPE` rows; the node library inserts typed structures from public OPC UA definitions; tests cover parent selection, batch insertion, and library insertion; typecheck + vitest + build green.

- [x] `UI-493` ✅ Manual Schema — address-space editor foundation
  Goal: make the Manual Schema editor communicate the current OPC UA address-space model clearly while preparing users for the richer node classes delivered by IS-176.
  Surface: `Manual Schemas` — editor.
  Work includes: explicit node-class selection for supported folders and variables; a primary structured add-variable form with name, data type, optional unit/description, explicit selected parent and Add action; complete editable variable details (type, scalar/array shape and access); and inline structural validation that explains what must be corrected before saving. Multiline `Name, TYPE` or tab-delimited entry is an advanced import/paste action only. Unsupported OPC UA node classes must be visibly identified as unavailable rather than silently represented as folders.
  Depends: UI-492; IS-176 for persistent Objects, Methods, References and NodeSet XML import.
  Done when: users can choose a supported node class knowingly, add one variable through a structured form with an explicit destination, inspect and edit supported node details, and see actionable duplicate/invalid-name/invalid-parent validation; typecheck + vitest + build green.

- [x] `UI-494` ✅ Manual Schema — expanded OPC UA catalog and multi-variable form
  Goal: let users manually build a realistic OPC UA server structure from known, documented parameters and reusable structures, without a comma/tab-delimited entry syntax.
  Surface: `Manual Schemas` — editor.
  Work includes: expanded searchable OPC UA/Prosys-inspired parameter catalog; documented simulation, static-data, analog/data-item, state and access examples; and an editable multi-variable row form with separate Name and Type controls.
  Depends: UI-493; IS-176.
  Done when: known catalog items are discoverable and inserted under an explicit parent; several variables are added from independently editable rows; no user-facing comma/tab syntax remains; typecheck + vitest + build green.

- [x] `UI-496` ✅ Synthetic wizard — hide recording prefill for Manual Schema
  Goal: keep the Synthetic wizard focused when a Manual Schema provides the structure: users should not see an unrelated recording selector that can expose an unhelpful technical ID.
  Surface: `Create Data Source Wizard` — Synthetic profile.
  Work includes: hide the recording-prefill selector and action for Manual Schema; preserve it for Existing source; cover both paths with tests.
  Depends: UI-491.
  Done when: Manual Schema shows only its selected structure and pattern controls; Existing source retains recording prefill; typecheck + vitest + build green.

- [x] `UI-497` ✅ Synthetic profile — simplify pattern and seed controls
  Goal: make the Synthetic profile easier to scan by keeping common patterns and core choices visible while moving technical generation settings behind explicit disclosure.
  Surface: `Create Data Source Wizard` — Synthetic profile.
  Work includes: a short default bulk-pattern list; plain-language pattern labels; and a collapsed Advanced generation settings section for repeatable results.
  Depends: UI-488.
  Done when: the default bulk list has Fixed value, Wave, and Random; advanced patterns appear after More patterns; repeatable-results settings stay hidden until requested; typecheck + vitest + build green.

- [x] `UI-498` ✅ Synthetic profile — make bulk patterns type-aware
  Goal: applying a bulk numeric pattern to mixed parameter types can leave STRING or BOOL rows on an unsupported pattern, making the row Pattern select appear blank and showing irrelevant Min/Max fields.
  Surface: `Create Data Source Wizard` — Synthetic profile.
  Work includes: map bulk Random to each selected type's supported random pattern; apply Wave and other numeric-only bulk patterns only to compatible numeric rows; preserve valid per-type controls and cover mixed-type selection.
  Depends: IS-180.
  Done when: applying Random or Wave to a mixed selection never leaves a row with a pattern absent from its dropdown or irrelevant fields; typecheck + vitest green.

- [x] `UI-495` ✅ Manual Schema — expand reusable OPC UA structure catalog
  Goal: expand the Manual Schema catalog with realistic, editable starter structures so users can build an OPC UA server from familiar equipment and data patterns instead of starting from empty folders.
  Surface: `Manual Schemas` — editor catalog.
  Work includes: Tank / vessel, Pump, Motor, Valve, Device identity, Simulation signals, Analog item, Condition values, Static data, and Access & state templates; clear English descriptions of each inserted structure; and user-facing copy that avoids internal technical terminology.
  Depends: UI-494.
  Done when: every listed template is searchable and inserts an editable folder with typed variables under the selected parent; representative template and discoverability tests pass; typecheck + vitest + build green.

- [x] `UI-499` ✅ Synthetic profile — BOOL random/alternating pattern shows a useless values-list editor
  Goal: `ENUM_CYCLE`/`RANDOM_CHOICE` for a BOOL node currently reuses the generic Add/Remove values-list editor, letting the user "curate" an arbitrary list when a boolean only ever has two possible values.
  Surface: `Synthetic profile` step — per-node pattern editor.
  Work includes: for boolean nodes, drop the Add/Remove list machinery for `ENUM_CYCLE`/`RANDOM_CHOICE` (mirroring the existing simple True/False `<select>` already used for `CONSTANT`), while non-boolean value-list types keep their current editor unchanged.
  Done when: BOOL nodes no longer show Add/Remove controls for alternating/random patterns; wire format (`values: ["true","false"]`) is unchanged; typecheck + vitest + build green.
- [x] `UI-500` ✅ Evidence list — resolve source/scenario names instead of raw UUIDs
  Goal: the Evidence list page shows a raw UUID for a single source and just a count ("N sources") for multiple, with no name resolution, unlike the Evidence detail page (IS-159) which already resolves sourceIds and recordingId to names.
  Surface: `Evidence` — list page, plus the detail page's remaining raw `scenarioId`.
  Work includes: resolve `sourceIds` to source names via `useDataSourcesStore` on the list page (listing all names for multi-source rows); resolve `scenarioId` to a scenario name on the detail page; raw ID remains an acceptable fallback when the referenced entity can't be found.
  Done when: list and detail pages show human-readable names wherever the referenced entity still exists; typecheck + vitest + build green.

- [x] `UI-501` ✅ Data Sources — don't show "Healthy" for a stopped source
  Goal: a disabled/stopped data source showed a green "Healthy" health badge on the Data Sources list and on the source detail pages, because `mapDataSource()` defaulted an absent health signal (`null`, returned for `STOPPED`/`STARTING` runtime state) to `"Healthy"`. Health only means something for an active run; a stopped source should show no health verdict instead of a misleading green one.
  Surface: `Data Sources` list page; `Data Source Detail` — overview tab and preview header.
  Work includes: make `DataSourceRow.health` nullable end-to-end; drop the `?? "Healthy"` fallback in `data-sources-store.ts`; render an em dash instead of a badge when health is `null` on the list page and both detail surfaces.
  Done when: a stopped/disabled source shows no health badge (or "—") anywhere, a running source keeps its real Healthy/Warning/Error badge unchanged, and typecheck + vitest are green.

- [x] `UI-502` ✅ Events tab — recognize RUN_COMPLETED/STOPPED/FAILED event types
  Goal: `humanize`/`typeToLevel`/`typeToCategory` in the per-source Events tab had no cases for the new `RUN_COMPLETED`/`RUN_STOPPED`/`RUN_FAILED` event types (IS-182), so they rendered as raw, uncategorized text instead of a clear label.
  Surface: `Data source detail` — Events tab.
  Work includes: explicit human-readable labels and an appropriate category/level for the three new run-completion event types (`RUN_FAILED` as error level, matching existing `ERROR`/`SOURCE_STOP` treatment; all three categorized as `runtime`).
  Depends: IS-182.
  Done when: the three event types render with clear labels and correct level/category; typecheck + vitest + build green.

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
