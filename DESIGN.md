# IoT Data Source Simulator UI Design

This file defines the product-level UI direction.

For implementation details, use:

- `UI_PLAN.md` for staged planning and coverage.
- `UI_SCREEN_SPECS.md` for screen requirements.
- `UI_TASKS.md` for development tasks.

## Quick Index

- Product direction
- Delivery model
- Architecture alignment
- Usage modes and roles
- Primary users
- Information architecture
- Core UX principles
- Main product flows
- Screen-level direction
- Interaction rules
- Edge and failure states
- Visual direction
- Accessibility
- Current decisions
- Open questions

## 1. Product Direction

The UI is an operational workbench for realistic industrial data-source
simulation.

The main product flow is:

1. Scan real source.
2. Record real behavior.
3. Replay through a simulated source.
4. Observe runtime behavior.
5. Export evidence.

The interface should feel practical, calm, and work-focused. It is not a landing
page, marketing dashboard, or decorative demo.

## 2. Delivery Model

This is a full product delivered in stages.

P0: first usable release slice.

- trusted local mode;
- no required login;
- OPC UA and Modbus TCP;
- Scan -> Record -> Replay;
- live values;
- evidence export;
- baseline accessibility and edge states.

P1: production-usable breadth.

- shared login/password;
- Admin/User shared-mode roles;
- manual schemas;
- synthetic data;
- deterministic replay/synthetic settings;
- import/export;
- recordings/samples management;
- clients, health, events;
- automated run visibility;
- retention;
- responsive/browser/platform baseline.

P2: advanced shared workflows.

- custom scenarios;
- faults;
- activity/audit history;
- advanced shared editing workflows;
- future identity provider and expanded-role compatibility.

## 3. Architecture Alignment

The UI must align with `ARCHITECTURE.md` without exposing implementation details.

UI implications:

- The Web UI talks to the backend only through public API surfaces.
- Users see product concepts, not worker, database, IPC, or storage concepts.
- Schema, values, recordings, samples, replay, scenarios, faults, and evidence
  use the protocol-neutral product model.
- Protocol-specific fields appear only in protocol configuration and schema
  details.
- Live values are an operational preview and can become stale/reconnecting.
- Recordings and evidence are captured timelines, not the same thing as live
  preview.
- Runtime events and user activity/audit are separate histories.
- Import/export artifacts show version compatibility and fail safely when newer
  than the product supports.
- Secrets, credentials, private keys, and PKI material never appear in summaries,
  activity, evidence, imports, or exports.
- Shared sign-in UI must remain compatible with OAuth2/OIDC-backed identity.
- Current shared roles are Admin/User, but role UI must not block later
  viewer/operator/editor/admin expansion.
- Browser UI should behave consistently on Linux, Windows, and macOS.
- Frontend must stay within the approved stack in `STACK.md`.

## 4. Usage Modes

### Trusted Local Mode

Purpose: fast local development, debugging, and reproduction.

Behavior:

- login can be disabled;
- user lands on project entry or project overview;
- local full-control behavior can be exposed;
- shared-role friction should be avoided.

### Shared Team Mode

Purpose: team-level QA, regression, support, and release validation.

P1 introduces:

- login/password;
- user identity;
- Admin/User role enforcement;
- visible run/edit/export initiators.

Shared-mode User:

- can see all projects;
- can observe projects, data-sources, schemas, recordings, samples, scenarios,
  evidence, runtime events, and activity;
- can start stopped data-sources;
- cannot stop, edit, delete, import, export, run scenarios, or manage users.

Shared-mode Admin:

- can create, edit, start, stop, delete, import, export, and administer.

## 5. Primary Users

QA engineer:

- reproduce bugs;
- run regression flows;
- simulate faults;
- export evidence.

Edge Device developer:

- create data-sources;
- inspect live values;
- debug clients and runtime events;
- verify protocol endpoint behavior.

Support or field engineer:

- import projects or prepared data;
- replay real behavior;
- collect evidence for handoff.

Shared environment operator:

- see who is running what;
- avoid disrupting other users;
- operate shared projects safely.

## 6. Information Architecture

Global layout:

- top bar with project, environment, global status, evidence shortcut, user/local
  mode area;
- left navigation;
- main content;
- collapsible runtime panel.

Navigation:

- Project Entry;
- Overview;
- Data Sources;
- Recordings & Samples;
- Scenarios;
- Evidence;
- Settings;
- Admin;
- Activity / Audit.

Runtime panel:

- recent runtime events;
- health alerts;
- active faults;
- connected clients summary;
- stale/reconnecting state.

Runtime state should never disappear completely.

## 7. Core UX Principles

Make runtime state obvious:

- running/stopped;
- enabled/disabled;
- connected clients;
- active replay/scenario;
- active faults;
- health/errors;
- latest value updates.

Separate configuration from execution:

- configured sources;
- active runs;
- saved scenarios;
- historical evidence.

Use domain language:

- project;
- data-source;
- schema;
- recording;
- sample;
- replay;
- scenario;
- fault;
- evidence.

Avoid vague labels such as resource, asset, entity, or job.

Prefer guided creation:

- data-source creation;
- real-source scan;
- manual schema definition;
- scenario creation;
- evidence export.

Design for repeated use:

- start project;
- stop all;
- run selected sources;
- replay last recording;
- export latest evidence.

Design for failure:

- external connections can fail;
- scans can be partial;
- recordings can be empty or interrupted;
- imports can be incompatible;
- live updates can become stale;
- exports can fail.

## 8. Main Product Flows

### Scan Real Source -> Record -> Replay

Stage: P0

Flow:

1. Open app.
2. Sign in only if shared mode requires it.
3. Select or create project.
4. Create data-source.
5. Choose protocol.
6. Choose Scan real source.
7. Enter connection details.
8. Test connection.
9. Scan structure.
10. Review schema.
11. Create simulated source.
12. Record real data.
13. Save recording/sample.
14. Assign replay.
15. Start simulated source.
16. Observe values, clients, runtime events.
17. Export evidence.

UI must make clear:

- real endpoint vs simulated endpoint;
- scan progress and warnings;
- recording duration and value count;
- replay target compatibility;
- connected clients;
- captured evidence state.

### Manual Or Synthetic Source

Stage: P1

Purpose: work when real devices are unavailable, sensitive, incomplete, or when
deterministic boundary data is needed.

UI must support:

- manual schema editor entry point;
- synthetic pattern/range/update settings;
- deterministic settings;
- validation before save/run.

### Deterministic Runs

Stage: P1

Purpose: repeat replay or generated behavior for regression.

UI must show:

- deterministic on/off state;
- seed or repeatability preset;
- timing mode;
- replay speed or loop behavior when relevant;
- repeat-run summary;
- note that client delivery timing can still vary.

Evidence must record deterministic settings.

### Scenarios And Faults

Stage: P2

Scenario builder approach:

- structured ordered step list;
- details panel per step;
- validation panel;
- no complex visual canvas.

Supported step types:

- start data-source;
- stop data-source;
- replay recording/sample;
- generate synthetic values;
- apply fault;
- clear fault;
- wait;
- mark event.

Fault UI must show:

- target;
- timing;
- parameters;
- clear behavior;
- affected sources/clients.

## 9. Screen-Level Direction

Project Overview:

- command center for project state;
- answers what exists, what is running, who started it, who is connected, what is
  unhealthy, and what recently happened.

Data Sources:

- dense table;
- visible status, protocol, endpoint, clients, initiator, health;
- Admin actions visible in shared mode;
- User can start only.

Create Data Source Wizard:

- one wizard for all protocols;
- protocol-specific fields inside shared steps;
- Scan real source is visually recommended;
- other source bases remain first-class.

Data Source Detail:

- Overview, Schema, Values, Clients, Events, Settings tabs;
- state, endpoint, health, and active behavior remain visible.

Schema Editor:

- full editor for editable schema parts;
- split layout with tree/table and details panel;
- validation and dependency impact before save;
- read-only mode for locked or unauthorized users.

Recordings & Samples:

- list, preview, import, export, assign to replay;
- version/format validation before import commit;
- timeline preview and warnings.

Evidence:

- automatic capture for every run;
- explicit export action;
- Report, Full bundle, Value timeline CSV;
- partial/large/failed states explained;
- no secrets or private keys.

Admin:

- users/known identities;
- role assignment;
- status and last activity;
- no assumption that product-owned password management exists.

Activity / Audit:

- separate from runtime events;
- filter by actor, action, object, project, and time.

## 10. Interaction Rules

Long-running actions:

- show current state;
- show initiator;
- show elapsed time;
- show latest successful step;
- show whether retry is safe.

Destructive/disruptive actions require confirmation:

- stop all;
- stop another user's run;
- delete source/recording/sample/scenario/evidence;
- overwrite or replace content during import;
- change schema identifiers used by dependencies.

Confirmation text must explain:

- affected object;
- connected clients;
- active runs;
- dependent recordings/samples/scenarios/evidence;
- whether action can be undone.

Concurrent editing:

- use automatic edit locks;
- first editor gets the lock;
- other users see read-only view;
- lock owner is visible;
- Admin can release stale locks;
- warn before leaving with unsaved changes.

Tables:

- search;
- filters;
- sorting;
- visible active filters;
- row actions;
- pagination or virtualization for large lists;
- helpful no-results state.

Notifications:

- inline alerts for fixable local issues;
- toasts for non-blocking success/background completion;
- persistent banners for stale data or reconnecting;
- dialogs for destructive/disruptive decisions.

## 11. Edge And Failure States

Login/session:

- invalid credentials;
- expired session;
- role changed during session;
- server unavailable.

Real-source scan:

- unreachable source;
- auth failure;
- partial scan;
- unknown type;
- duplicate identifiers;
- very large schema;
- stale scan result.

Recording/import/replay:

- disconnect during recording;
- no values;
- very large recording;
- unsupported file;
- newer-than-supported artifact version;
- timestamp/time zone inconsistency;
- no connected clients;
- replay target already running.

Schema editing:

- running source warning;
- dependency warnings;
- invalid edits;
- bulk edits;
- locked object;
- object deleted while viewed.

Runtime/observability:

- another user changes runtime state;
- client disconnects;
- fault affects values/connectivity;
- event stream reconnects;
- health unknown.

Evidence/export:

- capture still active;
- capture failed while run continues;
- export failed;
- no clients;
- large evidence;
- partial data.

## 12. Visual Direction

The UI should be dense but readable.

Use:

- calm operational layout;
- restrained colors;
- high-contrast runtime states;
- clear tables and timelines;
- strong empty states;
- consistent icons for run, stop, edit, duplicate, export, warning, health.

Avoid:

- marketing-style hero screens;
- oversized decorative cards for operational data;
- hidden hover-only critical actions;
- color-only status;
- unnecessary visual noise.

Color meaning:

- green: running/healthy;
- red: error/failure;
- amber: warning/fault;
- blue/neutral: informational active work;
- gray: disabled/inactive.

## 13. Accessibility

Baseline: WCAG 2.2 AA.

Requirements:

- keyboard reachable controls;
- visible focus;
- accessible names for tables/forms/dialogs/tabs/tree views;
- status changes announced without unstable focus movement;
- errors identify problem and recovery action;
- sufficient contrast;
- destructive confirmations understandable by screen reader users.

## 14. Current Decisions

- Product UI is mode-aware: trusted local and shared team.
- P0 can run without login in trusted local mode.
- P1 introduces shared login/password.
- Shared mode uses Admin/User initially.
- Shared User can observe and start data-sources only.
- Main flow is Scan real source -> Record -> Replay.
- Initial protocols are OPC UA and Modbus TCP.
- Data-source creation uses one protocol-extensible wizard.
- Schema tab includes a full schema editor.
- Evidence capture is automatic for every run.
- Evidence export is explicit.
- Import/export artifacts are version-aware.
- Runtime events and user activity/audit are distinct.
- Secrets/private keys/PKI material are never exposed in summaries or exports.
- Automated runs appear in runtime, activity, and evidence surfaces.
- Product is desktop-first, tablet-usable, and phone-limited unless expanded.

## 15. Open Questions

No blocking UI design questions remain.

Non-blocking refinements can happen during implementation:

- exact protocol field labels;
- exact validation copy;
- exact evidence file naming;
- exact deterministic setting labels;
- exact retention thresholds;
- exact responsive breakpoints.
