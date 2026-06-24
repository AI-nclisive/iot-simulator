# UI Screen Specifications

## Purpose

`UI_SCREEN_SPECS.md` defines the screen-level requirements for the Web UI.

This file explains what each page, flow surface, or shared UI surface must show,
what actions it must support, and what states it must handle.

## Surface Types

- `Page`: a full route inside the app shell.
- `Flow`: a guided multi-step or focused execution surface.
- `Shared surface`: a panel, modal, or reusable UI surface that appears in
  multiple places.

## Cross-Surface Contract

Every page inside the app shell should inherit the same structural rules:

- minimal top bar with product identity and utility entry points;
- collapsible left project rail with project context and primary navigation;
- central work area for the current task.

Every surface that shows mutable or runtime-sensitive content should account for:

- loading;
- empty state where relevant;
- validation problems where relevant;
- permission-restricted state where relevant;
- stale or reconnecting live state where relevant;
- partial failure where relevant;
- full failure where relevant.

Shared-mode surfaces must reflect the current role in the UI before action where
practical. Sensitive material such as credentials, secrets, private keys, and
PKI material must never appear in activity, evidence, summaries, or export
surfaces.

## Surface Map

Use this block as a quick index to the surfaces described below.

### Pages

- `Login`:
  enter shared mode.
- `Project Entry`:
  choose a project.
- `Project Overview`:
  see project state and next actions.
- `Data Sources List`:
  browse and operate sources.
- `Data Source Detail`:
  inspect one source in depth.
- `Recordings & Samples`:
  manage reusable captured data.
- `Scenarios`:
  browse saved scenarios and create new ones.
- `Scenario Run View`:
  observe one running scenario.
- `Evidence List`:
  find captured evidence.
- `Evidence Detail`:
  review one evidence artifact.
- `Activity View`:
  inspect user and automation history.
- `Settings`:
  manage project and environment settings.
- `Admin UI`:
  manage shared access.

### Flows

- `Create Data Source Wizard`:
  create a source through one guided entry.
- `Scan Real Source`:
  discover structure from a real endpoint.
- `Full Schema Editor`:
  fully edit a schema.
- `Recording Flow`:
  capture real behavior.
- `Replay Flow`:
  replay saved behavior through a source.
- `Scenario Builder`:
  build a scenario.

### Shared Surfaces

- `Deterministic Run Settings`:
  configure repeatable behavior.
- `Runtime Dashboard`:
  keep live runtime visible everywhere.
- `Automated Run Visibility`:
  show automation-run presence across pages.
- `Credential Handling`:
  safely collect and display connection secrets.
- `Retention & Cleanup`:
  manage large or old artifacts safely.
- `Notifications`:
  deliver transient and persistent feedback.

## Access And Entry

### Login

Type: `Page`

Primary purpose:

- enter a shared simulator environment.

Entry points:

- initial entry when shared authentication is enabled;
- session expiration;
- explicit sign-out return.

Must show:

- environment or server name;
- login field;
- password field;
- sign-in action;
- authentication error area.

Primary actions:

- sign in;
- retry after failure.

Key states:

- empty;
- loading;
- invalid credentials;
- server unavailable;
- session expired.

Shared behavior:

- this surface is skipped in trusted local mode;
- the layout should remain compatible with future provider-based identity.

Complete when:

- shared mode blocks project content until authentication succeeds;
- errors are clear and recoverable;
- keyboard-only sign-in works.

### Project Entry

Type: `Page`

Primary purpose:

- choose the project to work in.

Entry points:

- initial local entry;
- first screen after shared login;
- explicit return from inside a project.

Must show:

- project list;
- project status or recent activity summary;
- create action;
- import action;
- open action.

Primary actions:

- open project;
- create project;
- import project.

Key states:

- no projects;
- loading;
- import in progress;
- import failed;
- project unavailable.

Shared behavior:

- all users can see all projects;
- in shared mode, create and import actions are Admin-only.

Complete when:

- users can choose a project without ambiguity;
- recent project activity helps orient the next action;
- the empty state points clearly toward starting work.

## Project Pages

### Project Overview

Type: `Page`

Primary purpose:

- act as the default landing route for the runtime dashboard.

Entry points:

- default landing page after project selection;
- return point from deeper surfaces.

Must show:

- the runtime dashboard as the main page content in the lightest version;
- if extra content exists, it should stay minimal and support orientation only.

Primary actions:

- open data sources;
- open recordings or samples;
- inspect activity;
- open evidence.

Key states:

- dashboard only;
- empty;
- ready;
- warning;
- error;
- stale live data.

Shared behavior:

- Admin gets full operational and configuration actions;
- User can observe and operate runtime — start and stop data-sources and runs.

Complete when:

- landing on `Overview` feels like opening one simple dashboard rather than a
  second page competing with the shell.

### Data Sources List

Type: `Page`

Primary purpose:

- browse, filter, and operate all sources in the project.

Entry points:

- main navigation;
- drill-in from Overview.

Must show:

- dense source table;
- protocol and endpoint summary;
- current status;
- connected-client summary where available;
- health summary;
- initiator or last-operator context where relevant;
- row actions.

Primary actions:

- filter and search;
- create source;
- open detail;
- start source;
- stop source;
- duplicate source;
- delete source.

Key states:

- empty;
- loading;
- running items;
- locked item;
- error on one or more rows;
- large result set.

Shared behavior:

- User can open detail and start/stop sources;
- Admin can use full row actions (configure, duplicate, delete).

Complete when:

- the table supports rapid scanning and repeated daily use;
- row-level runtime state is obvious without opening each source.

### Data Source Detail

Type: `Page`

Primary purpose:

- inspect one source deeply while keeping runtime state visible.

Entry points:

- Data Sources list;
- Overview alerts or runtime links;
- evidence and activity drill-ins.

Must show:

- source identity and status header;
- endpoint summary;
- parameter count and schema scale context;
- tabs or segmented sections for Overview, Schema, Values, Clients, Events, and
  Settings;
- active runtime context that stays visible while switching tabs.

Primary actions:

- start source;
- stop source;
- open schema editor;
- start recording;
- configure replay;
- inspect clients and events.

Key states:

- stopped;
- running;
- error;
- stale live state;
- read-only;
- locked by another edit session.

Shared behavior:

- User can inspect all tabs, start/stop the source, and start replay runs;
- Admin can use mutation and configuration actions.

Complete when:

- the user can stay oriented to runtime state while moving between tabs;
- configuration and observation do not blur together;
- large parameter sets still feel navigable instead of overwhelming.

### Recordings & Samples

Type: `Page`

Primary purpose:

- manage reusable captured data for replay and comparison.

Entry points:

- main navigation;
- source detail after recording completion;
- replay setup drill-in.

Must show:

- list with filters;
- source association;
- capture origin;
- preview area;
- import action;
- export action;
- assign-to-replay action.

Primary actions:

- filter;
- preview;
- import prepared data;
- export allowed artifacts;
- assign artifact to replay.

Key states:

- empty;
- loading;
- import validation;
- incompatible artifact;
- unsupported newer version;
- no results.

Shared behavior:

- User sees a read-only management surface;
- Admin can import, export, and assign.

Complete when:

- reusable data is easy to browse and attach back into runtime work;
- artifact compatibility is clear before commit.

### Scenarios

Type: `Page`

Primary purpose:

- browse saved scenarios and start creating or opening one.

Entry points:

- main navigation;
- drill-in from Overview or activity.

Must show:

- scenario list;
- scenario status summary;
- last run summary where available;
- create action;
- open action.

Primary actions:

- create scenario;
- open scenario;
- duplicate scenario;
- run scenario;
- stop scenario.

Key states:

- empty;
- loading;
- no results;
- locked scenario;
- run in progress.

Shared behavior:

- User can view saved scenarios and run/stop them;
- Admin can additionally create, edit, and duplicate scenarios.

Complete when:

- the scenarios area has a clear landing surface instead of dropping the user
  directly into a builder with no context.

### Evidence List

Type: `Page`

Primary purpose:

- find and filter captured evidence artifacts.

Entry points:

- main navigation;
- Overview recent evidence;
- runtime completion links.

Must show:

- evidence table;
- status;
- run origin;
- initiator;
- source or scenario context;
- export state.

Primary actions:

- filter;
- open evidence;
- export allowed evidence.

Key states:

- empty;
- capturing;
- ready;
- exported;
- export failed;
- no results.

Shared behavior:

- all users can inspect evidence;
- export actions are Admin-only in shared mode.

Complete when:

- users can quickly find evidence by source, run, initiator, or status.

### Evidence Detail

Type: `Page`

Primary purpose:

- review one evidence artifact and understand what happened.

Entry points:

- Evidence list;
- Overview evidence links;
- runtime completion links.

Must show:

- summary;
- run origin;
- initiator;
- captured timeline or event summary;
- clients;
- faults or errors where relevant;
- export options;
- completeness state.

Primary actions:

- export allowed formats;
- move to related source or run context.

Key states:

- capturing;
- ready;
- partial;
- export failed;
- large evidence.

Shared behavior:

- all users can inspect;
- Admin can export and perform cleanup actions where allowed.

Complete when:

- it is obvious what the evidence represents, whether it is complete, and how
  it relates back to the originating work.

### Activity View

Type: `Page`

Primary purpose:

- inspect user and automation activity over time.

Entry points:

- main navigation;
- drill-in from Overview or Admin.

Must show:

- filters;
- actor;
- action;
- object;
- project context;
- time;
- links back to related objects.

Primary actions:

- filter;
- open related object.

Key states:

- empty;
- loading;
- filtered;
- stale.

Shared behavior:

- this is a read-only surface for shared visibility;
- runtime events remain separate from user activity and are not merged into one
  undifferentiated stream.

Complete when:

- users can answer who did what, when, and to which object without confusion
  between runtime and audit history.

### Settings

Type: `Page`

Primary purpose:

- manage project-level and environment-level settings.

Entry points:

- main navigation;
- project administration actions.

Must show:

- project settings area;
- environment settings area;
- import and export entry points where relevant;
- defaults and metadata.

Primary actions:

- edit allowed settings;
- save;
- export project;
- import project where permitted.

Key states:

- loading;
- saved;
- validation error;
- permission denied.

Shared behavior:

- User sees configuration but cannot mutate;
- Admin can change settings within allowed scope.

Complete when:

- project settings and wider environment settings are clearly separated;
- risky changes do not hide inside generic forms.

### Admin UI

Type: `Page`

Primary purpose:

- manage shared access and role assignment.

Entry points:

- main navigation in shared environments.

Must show:

- users or known identities list;
- current role;
- status;
- recent activity hint;
- role change controls.

Primary actions:

- filter users;
- change role;
- review status.

Key states:

- empty;
- loading;
- validation error;
- role changed;
- permission denied.

Shared behavior:

- Admin-only surface;
- the UI must not assume product-owned password lifecycle management if identity
  moves to external providers later.

Complete when:

- access management is understandable without exposing identity-system internals.

## Guided Flows And Editors

### Create Data Source Wizard

Type: `Flow`

Primary purpose:

- create a source through one extensible guided flow.

Entry points:

- Overview primary action;
- Data Sources page primary action.

Must show:

- protocol selection;
- source-basis selection;
- setup step;
- schema step;
- runtime-behavior step;
- review step.

Primary actions:

- next;
- back;
- cancel;
- create;
- test details where relevant.

Key states:

- validation error;
- connection test result;
- partial discovery warning;
- incompatible import input;
- review-ready.

Shared behavior:

- User cannot enter this flow for mutation in shared mode;
- permission denial should be explained clearly rather than failing late.

Complete when:

- the same wizard model can support current and future protocols without turning
  into unrelated per-protocol flows;
- progress and remaining work stay obvious.

### Scan Real Source

Type: `Flow`

Primary purpose:

- discover structure from a real endpoint.

Entry points:

- scan branch inside the creation wizard.

Must show:

- secure connection form;
- connection test result;
- scan progress;
- discovery summary;
- schema preview;
- continue action into review.

Primary actions:

- test connection;
- start scan;
- retry;
- accept discovered structure.

Key states:

- unreachable endpoint;
- authentication failure;
- partial scan;
- large schema;
- unknown data type;
- stale scan result.

Shared behavior:

- credential entry obeys shared credential-handling rules;
- entered secrets must never leak into summaries or activity.

Complete when:

- users can clearly tell what real endpoint was scanned, what was found, and
  what still needs review before source creation.

### Full Schema Editor

Type: `Flow`

Primary purpose:

- give full manual control over an existing schema.

Entry points:

- Data Source Detail;
- manual-schema branch after creation;
- dependency or validation drill-in.

Must show:

- editing toolbar;
- structure tree or table built for large parameter sets;
- details panel;
- validation summary;
- unsaved-change indicator.

Primary actions:

- edit structure;
- edit field details;
- save;
- discard;
- inspect validation and dependencies.

Key states:

- loading;
- empty;
- invalid;
- running-source warning;
- dependency warning;
- locked by another user.

Shared behavior:

- User sees a read-only editor surface in shared mode;
- Admin can edit with shared-concurrency protection.

Complete when:

- this surface feels like a real editor instead of a narrow form patch;
- the user can understand impact before saving;
- hundreds or thousands of parameters remain searchable and inspectable.

### Recording Flow

Type: `Flow`

Primary purpose:

- capture real behavior over time.

Entry points:

- Data Source Detail;
- scan or setup continuation where recording is the next promoted step.

Must show:

- source summary;
- recording state;
- start and stop controls;
- duration;
- value count;
- last-received hint;
- save result metadata.

Primary actions:

- start recording;
- stop recording;
- save result;
- discard failed or unwanted capture.

Key states:

- ready;
- recording;
- no values yet;
- disconnected;
- partial save;
- save-ready.

Shared behavior:

- visible authorship matters because recording produces reusable team artifacts;
- shared-mode permissions follow source-operation rules.

Complete when:

- the user can tell whether capture is active, useful, partial, or ready to
  become a reusable artifact.

### Replay Flow

Type: `Flow`

Primary purpose:

- replay a recording or sample through a simulated source.

Entry points:

- Data Source Detail;
- Recordings & Samples;
- follow-up action after recording save.

Must show:

- recording or sample selector;
- target source;
- compatibility summary;
- run progress;
- client activity;
- runtime events;
- evidence destination or result.

Primary actions:

- choose artifact;
- configure replay;
- start replay;
- stop replay.

Key states:

- no target available;
- target already active;
- no clients;
- replay running;
- completed;
- failed.

Shared behavior:

- User can start and stop a configured replay; configuring it is Admin-only;
- Admin can set up, configure, and execute replay.

Complete when:

- replay feels like a direct continuation of recording instead of a disconnected
  feature;
- users can see compatibility and impact before they start.

### Deterministic Run Settings

Type: `Shared surface`

Primary purpose:

- configure repeatable replay or generated behavior.

Entry points:

- replay setup;
- synthetic setup;
- scenario configuration.

Must show:

- deterministic toggle;
- seed or repeatability preset;
- ordering or timing mode;
- concise explanation of repeatability scope.

Primary actions:

- enable or disable deterministic behavior;
- enter settings;
- confirm settings.

Key states:

- off;
- enabled;
- invalid seed;
- incompatible setup.

Shared behavior:

- settings should be inspectable in evidence or run summary after use;
- the UI must not promise client delivery timing guarantees the system does not
  guarantee.

Complete when:

- users can understand what repeatability means in product terms, not backend
  terms.

### Scenario Builder

Type: `Flow`

Primary purpose:

- build a structured scenario from supported steps.

Entry points:

- Scenarios;
- duplicate-scenario action.

Must show:

- ordered step list;
- step details panel;
- validation summary;
- save action;
- run action.

Primary actions:

- add step;
- edit step;
- reorder step;
- save scenario;
- run scenario.

Key states:

- draft;
- invalid;
- ready;
- locked.

Shared behavior:

- User can inspect but not edit scenarios; running a saved scenario is allowed;
- Admin can edit and execute scenarios.

Complete when:

- supported step types fit one consistent builder model;
- users understand why a scenario is or is not runnable.

### Scenario Run View

Type: `Page`

Primary purpose:

- observe one active or completed scenario run.

Entry points:

- Scenarios;
- activity drill-in;
- runtime links.

Must show:

- run summary;
- current step;
- ordered step timeline;
- sources involved;
- faults where relevant;
- clients;
- events;
- evidence state.

Primary actions:

- stop run where permitted;
- open related source;
- open evidence.

Key states:

- queued;
- running;
- stopped;
- failed;
- completed;
- stale.

Shared behavior:

- all users can inspect scenario run state;
- stopping a run is available to both User and Admin.

Complete when:

- a user can tell what the scenario is doing right now and what happened
  immediately before.

## Shared Surfaces

### Runtime Dashboard

Type: `Shared surface`

Primary purpose:

- keep live runtime visible on `Overview` without overloading deeper project
  pages.

Entry points:

- `Overview`.

Must show:

- active runs;
- active process where relevant, such as recording, replay, or scenario;
- source scale context, such as parameter count;
- evidence state inside each active run;
- initiator or author context where meaningful;
- short run recency or time context;
- optional pinned parameter preview instead of a full raw value dump;
- quick links back to affected objects.

Primary actions:

- open related object;
- open run-related evidence where available.

Key states:

- no active runtime;
- active;
- partial evidence state.

Shared behavior:

- authorship and initiator context should appear where meaningful;
- evidence state belongs to the relevant run card rather than a separate recent
  evidence shelf;
- this surface stays compact and readable, while deeper runtime history and
  alerts live on dedicated detail surfaces.

Complete when:

- users can return to `Overview` and regain live operational awareness quickly;
- users can tell whether a run represents a small stream or a very large
  parameter set without leaving the dashboard.

### Automated Run Visibility

Type: `Shared surface`

Primary purpose:

- make automation-driven runs visible anywhere equivalent manual runs are shown.

Entry points:

- Overview;
- runtime surfaces;
- evidence surfaces;
- activity surfaces.

Must show:

- automation initiator label;
- run state;
- related source or scenario;
- evidence state.

Primary actions:

- open related object;
- open evidence.

Key states:

- queued;
- running;
- failed;
- completed;
- stopped.

Shared behavior:

- automated work should never look like anonymous manual activity.

Complete when:

- automation activity is first-class and traceable across the product.

### Credential Handling

Type: `Shared surface`

Primary purpose:

- safely collect and display connection-sensitive fields.

Entry points:

- scan flow;
- manual source setup;
- settings where credentials are relevant.

Must show:

- masked sensitive fields;
- saved or session-only state where supported;
- clear action;
- validation or permission errors.

Primary actions:

- enter sensitive value;
- clear value;
- confirm use.

Key states:

- missing;
- invalid;
- saved;
- session-only;
- permission failure.

Shared behavior:

- sensitive values must never leak into summaries, evidence, activity, or
  exports.

Complete when:

- users can safely provide required secrets without ambiguity about visibility
  or persistence.

### Retention & Cleanup

Type: `Shared surface`

Primary purpose:

- manage large, old, or unused artifacts safely.

Entry points:

- Settings;
- evidence and artifact management surfaces.

Must show:

- size;
- age;
- last-used signal where available;
- dependency warning;
- cleanup or archive action.

Primary actions:

- cleanup;
- archive where supported;
- review dependencies.

Key states:

- normal;
- large;
- old;
- dependency warning;
- cleanup failed.

Shared behavior:

- destructive cleanup requires explicit confirmation;
- shared impact should be visible before commit.

Complete when:

- users can understand storage impact and deletion risk before acting.

### Notifications

Type: `Shared surface`

Primary purpose:

- deliver transient and persistent feedback consistently.

Entry points:

- any screen or flow that creates, mutates, runs, imports, exports, or fails.

Must show:

- inline alerts;
- success toasts;
- persistent banners;
- confirmation dialogs.

Primary actions:

- dismiss non-blocking feedback;
- confirm risky action;
- navigate to related detail where relevant.

Key states:

- success;
- warning;
- error;
- reconnecting;
- stale.

Shared behavior:

- blocking and non-blocking feedback must be visually distinct;
- shared-impact warnings should persist until the user can reasonably act on
  them.

Complete when:

- users can tell what happened, what still needs attention, and whether action
  is required immediately.
