# UI Screen Specifications

This file describes what each screen must support.

Readable rule: each screen has the same short structure:

- stage;
- user goal;
- main UI;
- required states;
- done when.

Use this file with `DESIGN.md` and `UI_TASKS.md`.

## Quick Index

Common rules:

- Common Screen Rules
- Shared Mode Permissions

P0 screens:

- Project Entry
- Project Overview
- Data Sources List
- Create Data Source Wizard
- Scan Real Source
- Recording Flow
- Replay Flow
- Data Source Detail
- Evidence List
- Evidence Detail
- Credential Handling

P1 screens:

- Login
- Deterministic Run Settings
- Full Schema Editor
- Recordings & Samples
- Settings
- Admin UI
- Automated Runs Visibility
- Retention / Cleanup
- Notifications

P2 screens:

- Scenario Builder
- Scenario Run View
- Activity / Audit View

## Common Screen Rules

Every screen must follow these rules:

- Use the same app shell: top bar, left navigation, main content, runtime panel.
- Show deployment mode clearly: trusted local or shared team.
- Treat login/password as P1 shared-mode behavior.
- Show runtime state with last updated time where relevant.
- Show initiator/owner for runs, scenarios, edits, and evidence.
- Show progress for long-running work.
- Explain errors in user terms.
- Use tables for dense operational lists.
- Keep controls keyboard reachable and focus visible.
- Never use color as the only status signal.
- Keep locked objects readable but not editable for other users.
- Keep live preview and captured timelines visually distinct.
- Keep runtime events and user activity/audit labeled separately.
- Show artifact version compatibility before import/export commit.
- Never expose secrets, credentials, private keys, or PKI material.
- Keep browser UI usable on Linux, Windows, and macOS.

## Shared Mode Permissions

This matrix applies after shared-mode authentication is introduced.

Trusted local mode can expose full-control behavior.

Admin:

- view projects and runtime state;
- start and stop data-sources;
- create, edit, duplicate, and delete data-sources;
- create and edit schemas;
- import and export projects, recordings, samples, and evidence;
- run and stop scenarios;
- manage users, roles, and settings;
- view activity/audit.

User:

- view projects and runtime state;
- view schemas, recordings, samples, scenarios, evidence, runtime events, and
  user activity;
- start stopped data-sources;
- cannot stop, edit, delete, import, export, run scenarios, or manage users.

## Screen Specs

### Login

Stage: P1

User goal: enter a shared simulator environment.

Main UI:

- environment name;
- login field;
- password field;
- sign in action;
- server/authentication error area.

Required states:

- empty;
- loading;
- invalid credentials;
- server unavailable;
- session expired.

Done when:

- trusted local mode can skip this screen;
- shared mode blocks project content until authentication;
- errors are clear;
- keyboard sign-in works;
- layout can later support OAuth2/OIDC/provider metadata.

### Project Entry

Stage: P0

User goal: choose where to work.

Main UI:

- project list;
- create project;
- import project;
- active/running status;
- recent activity.

Required states:

- no projects;
- loading;
- import progress;
- import failed;
- project unavailable.

Done when:

- local users or signed-in shared users can see projects and running status;
- create/import is Admin-only in shared mode;
- empty state offers create/open/import;
- project lifecycle can be added without changing the entry model.

### Project Overview

Stage: P0

User goal: understand project state immediately.

Main UI:

- project health and global state;
- primary actions;
- active runs;
- alerts;
- data-source summary;
- connected clients summary;
- runtime panel;
- recent activity.

Required states:

- empty;
- ready;
- running;
- warning;
- error;
- stale data.

Done when:

- users can answer what exists, what is running, who started it, who is connected,
  and what is unhealthy;
- Admin can use full actions in shared mode;
- User can view and start stopped data-sources only.

### Data Sources List

Stage: P0

User goal: view or manage simulated sources.

Main UI:

- filters;
- dense source table;
- runtime summary;
- row actions.

Required states:

- empty;
- loading;
- running;
- error;
- locked;
- large list.

Done when:

- each row shows protocol, status, endpoint, clients, initiator, and health;
- User can start only in shared mode;
- Admin can create, start, stop, duplicate, edit, and remove.

### Create Data Source Wizard

Stage: P0

User goal: create a source through one extensible flow.

Main UI:

- protocol step;
- source basis step;
- structure step;
- behavior step;
- endpoint/name step;
- review step.

Required states:

- validation error;
- connection test;
- partial scan;
- incompatible sample;
- review ready.

Done when:

- OPC UA, Modbus TCP, and future protocols fit the same wizard;
- Scan real source is recommended but not the only path;
- back/next/cancel preserve input;
- shared-mode User cannot open the wizard except through a permission explanation.

### Scan Real Source

Stage: P0

User goal: capture structure from a real device.

Main UI:

- masked connection form;
- test connection;
- scan progress;
- schema preview;
- create source action.

Required states:

- unreachable;
- authentication failure;
- partial scan;
- large schema;
- unknown data type;
- stale scan result.

Done when:

- real endpoint and simulated endpoint are clearly distinct;
- retry does not lose entered connection details;
- partial scan is usable with warnings when appropriate.

### Recording Flow

Stage: P0

User goal: capture real behavior over time.

Main UI:

- source summary;
- start/stop controls;
- duration;
- value count;
- timeline preview;
- save metadata.

Required states:

- ready;
- recording;
- no values;
- disconnected;
- partial save;
- save ready.

Done when:

- duration, value count, and last received value are visible;
- useful partial recordings can be saved with a warning;
- recording and live preview are not described as the same thing.

### Replay Flow

Stage: P0

User goal: replay recorded/sample data through a simulated source.

Main UI:

- recording/sample selector;
- target source;
- compatibility check;
- progress;
- clients;
- events.

Required states:

- no clients;
- target already running;
- replay running;
- complete;
- failed.

Done when:

- replacement impact is clear before replay starts;
- target compatibility is shown;
- replay can feed evidence.

### Deterministic Run Settings

Stage: P1

User goal: repeat replay or generated behavior predictably.

Main UI:

- deterministic toggle;
- seed or preset;
- timing mode;
- replay behavior;
- repeat-run summary.

Required states:

- off;
- enabled;
- invalid seed;
- incompatible target;
- completed.

Done when:

- replay/synthetic value content, ordering, and timing setup can be repeated;
- evidence records deterministic settings;
- UI explains that connected client delivery timing can still vary.

### Data Source Detail

Stage: P0/P1

User goal: inspect and operate one source.

Main UI:

- Overview tab;
- Schema tab;
- Values tab;
- Clients tab;
- Events tab;
- Settings tab.

Required states:

- stopped;
- running;
- error;
- stale;
- locked;
- read-only.

Done when:

- source state, endpoint, health, and active behavior stay visible;
- tabs can grow from P0 into P1 without changing navigation.

### Full Schema Editor

Stage: P1

User goal: edit any editable part of a schema.

Main UI:

- toolbar;
- schema tree/table;
- details panel;
- validation summary;
- unsaved changes indicator.

Required states:

- empty;
- loading;
- invalid;
- running-source warning;
- dependency warning;
- locked.

Done when:

- Admin can edit identifiers, types, values, metadata, and read/write behavior
  where supported;
- User sees read-only mode in shared mode;
- dependency impact is shown before save.

### Recordings & Samples

Stage: P1

User goal: view or manage reusable data.

Main UI:

- list;
- filters;
- import;
- export;
- preview;
- assign to replay.

Required states:

- empty;
- loading;
- import validation;
- incompatible file;
- newer unsupported version;
- no results.

Done when:

- Admin can import prepared files and preview before save;
- version/format is validated before commit;
- User sees read-only mode in shared mode.

### Scenario Builder

Stage: P2

User goal: build a repeatable test flow.

Main UI:

- ordered step list;
- step details panel;
- validation panel;
- save/run actions.

Required states:

- draft;
- invalid;
- ready;
- locked.

Done when:

- run is disabled until validation passes;
- faults, waits, replay, synthetic, start/stop, and marker steps fit one model.

### Scenario Run View

Stage: P2

User goal: observe an active scenario.

Main UI:

- run summary;
- step timeline;
- sources;
- clients;
- faults;
- events;
- evidence state.

Required states:

- running;
- stopped;
- failed;
- completed;
- stale.

Done when:

- current step, initiator, faults, clients, events, and evidence state are visible.

### Evidence List

Stage: P0

User goal: find captured evidence.

Main UI:

- evidence table;
- filters;
- status;
- export state.

Required states:

- empty;
- capturing;
- ready;
- exported;
- export failed;
- no results.

Done when:

- users can find runs by initiator, project, scenario, and status;
- export is Admin-only in shared mode.

### Evidence Detail

Stage: P0

User goal: review and export what happened.

Main UI:

- summary;
- captured timeline;
- clients;
- faults/errors;
- deterministic settings when relevant;
- export options.

Required states:

- capturing;
- ready;
- partial;
- export failed;
- large evidence.

Done when:

- Report, Full bundle, and Value timeline CSV are clear;
- missing or partial sections are explained;
- secrets and private keys are excluded.

### Settings

Stage: P1

User goal: manage project/environment settings.

Main UI:

- project details;
- import/export project;
- defaults;
- environment info.

Required states:

- loading;
- saved;
- validation error;
- permission denied.

Done when:

- project settings and environment settings are separated;
- mutation is Admin-only in shared mode.

### Admin UI

Stage: P1

User goal: manage shared access.

Main UI:

- users/known identities list;
- role assignment;
- status;
- last activity.

Required states:

- empty;
- loading;
- validation error;
- role changed;
- permission denied.

Done when:

- Admin can manage access and roles;
- product-owned password management is not assumed.

### Activity / Audit View

Stage: P2

User goal: inspect team and automation history.

Main UI:

- filters;
- event list;
- linked objects.

Required states:

- empty;
- loading;
- filtered;
- stale.

Done when:

- shared users can filter user/automation activity by actor, action, object,
  project, and time;
- runtime events and user activity stay labeled separately.

### Automated Runs Visibility

Stage: P1

User goal: understand automated test activity.

Main UI:

- automation initiator;
- source;
- run status;
- evidence state.

Required states:

- queued;
- running;
- failed;
- completed;
- stopped.

Done when:

- automated runs are visible wherever equivalent manual runs are visible.

### Credential Handling

Stage: P0/P1

User goal: connect to real sources safely.

Main UI:

- masked fields;
- saved/session-only indicator;
- clear action;
- permission/auth errors.

Required states:

- missing;
- invalid;
- saved;
- session-only;
- permission failure.

Done when:

- secrets, credentials, private keys, and PKI material never appear in summaries,
  activity, evidence, imports, or exports.

### Retention / Cleanup

Stage: P1

User goal: manage large reusable artifacts.

Main UI:

- size;
- age;
- last used;
- cleanup/archive action;
- dependency warnings.

Required states:

- normal;
- large;
- old;
- dependency warning;
- cleanup failed.

Done when:

- users understand storage impact and deletion dependencies.

### Notifications

Stage: P1

User goal: understand transient and persistent issues.

Main UI:

- inline alert;
- toast;
- persistent banner;
- confirmation dialog.

Required states:

- success;
- warning;
- error;
- reconnecting;
- stale.

Done when:

- blocking and non-blocking feedback are visually distinct.
