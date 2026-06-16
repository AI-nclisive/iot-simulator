# IoT Data Source Simulator MVP UI Design

## Purpose

This document defines the MVP user interface design for the IoT Data Source Simulator.

The goal of the MVP UI is to let QA engineers, Edge Device developers, support engineers, and team operators create realistic simulated industrial data sources, run them, observe behavior, and export evidence with as little friction as possible.

Technical architecture is intentionally out of scope for this document and should be described separately.

## MVP Product Shape

The MVP should feel like a practical workbench, not a marketing product.

Users should be able to:

- open or create a simulator project;
- create OPC UA and Modbus TCP data-sources;
- define schemas manually or from a real source scan;
- run one or more data-sources;
- observe live values, clients, health, and runtime events;
- replay recorded data or generate synthetic data;
- build simple scenarios with faults;
- export evidence after a run.

The UI should optimize for clarity, repeatability, and confidence. A user should always understand what is configured, what is running, what is connected, and what happened.

The main product emphasis for the MVP is Scan real source -> Record -> Replay. This path should be visually and behaviorally prominent because it turns real industrial behavior into reusable simulator assets.

Manual creation and synthetic data generation are also first-class UI paths. They are essential when real devices are unavailable, sensitive, incomplete, or when deterministic boundary cases are needed.

## Recommended Usage Model For MVP

### Primary Recommendation: Shared Web UI

The MVP should be designed around a shared Web UI that users open by URL.

The user experience should be:

1. User opens the simulator Web UI.
2. User signs in with login and password.
3. User lands on a project list or project overview.
4. User can see shared team activity, active runs, and available projects.
5. User can create, edit, run, observe, and export according to their permissions.

This is the most useful starting point for a production-usable MVP because:

- multiple QA engineers, developers, and support engineers can work in one environment;
- team members can see who started runs, scenarios, and data-sources;
- evidence can be attributed to the person who initiated the run;
- shared projects can become repeatable team assets;
- the UI can still support local or personal deployments without changing its core design.

Deployment and infrastructure details are out of scope for this UI design document.

### Login Requirement

Login and password are required for the MVP.

The login experience should be simple:

- login field;
- password field;
- clear error state for invalid credentials;
- clear error state for unavailable server;
- optional remember-me behavior if allowed by product/security decisions;
- visible product/environment name so users know where they are signing in.

After login, the user menu should show:

- user name;
- role;
- logout action.

### MVP Roles

The MVP should start with two roles: Admin and User.

Admin:

- manages users;
- assigns Admin or User role;
- manages environment-level settings;
- can create, edit, run, stop, delete, import, export, and observe across projects.

User:

- can see all projects in the environment;
- can create, edit, run, stop, import, export, and observe projects;
- can start and stop data-sources;
- can run scenarios;
- can export evidence;
- cannot manage users or environment-level settings.

The UI should keep role handling simple. Admin-only areas should be visible only to Admin users, or shown as unavailable with a clear reason when discoverability is useful.

Project access is open to all signed-in Users in the MVP. The UI should rely on visible authorship, team activity, confirmations, and evidence history rather than project-level permissions.

### Team Collaboration Model

The UI should assume that multiple users may view, edit, and operate the same environment at the same time.

The interface must show:

- who started a data-source run;
- who started a scenario;
- who stopped or changed an active run;
- who last edited a project, data-source, recording, sample, or scenario;
- who initiated evidence capture or export.

When one user is editing an object that another user opens, the UI should show a visible editing indicator.

Examples:

- "Anna is editing this scenario";
- "Last edited by Ivan 4 minutes ago";
- "Scenario running, started by Maria";
- "Stopping this run will affect 3 connected clients".

For the MVP, collaborative editing should use automatic edit locks instead of real-time multi-user editing.

## Design Principles

### Make Runtime State Obvious

The simulator is a runtime tool. The UI must make live state highly visible:

- running or stopped;
- enabled or disabled;
- connected clients;
- active replay or scenario;
- active faults;
- health and errors;
- latest value updates.

State should be visible without opening logs.

### Separate Configuration From Execution

Users need to know whether they are editing setup or controlling a live run.

The UI should distinguish:

- configured data-sources;
- currently running data-sources;
- saved scenarios;
- active scenario run;
- historical evidence.

### Keep Industrial Concepts Concrete

The UI should use domain words consistently:

- project;
- data-source;
- schema;
- recording;
- sample;
- replay;
- scenario;
- fault;
- evidence.

Avoid abstract labels like "resource", "asset", "entity", or "job" when a more specific simulator term exists.

### Prefer Guided Creation

Creating a data-source or scenario has enough decisions that users should be guided through it.

Use step-by-step flows for:

- creating a data-source;
- scanning a real source;
- manually defining a schema;
- creating a scenario;
- exporting evidence.

### Design For Repeated Use

The app will be used during debugging and regression work. Common actions should be fast after the first setup:

- start project;
- stop all;
- run selected sources;
- replay last recording;
- rerun last scenario;
- export latest evidence.

### Show Safe Defaults

The UI should guide users toward safe, understandable defaults:

- mandatory login before project access;
- stopped data-sources after project open;
- explicit start action;
- clear endpoint preview before running;
- visible initiator for active runs and scenarios;
- confirmation before actions that affect other users;
- deterministic scenario option visible when relevant;
- evidence export suggested after a run.

### Design For Failure And Recovery

Industrial simulation workflows depend on networks, real devices, long-running scans, large schemas, shared users, and runtime state. The UI should treat failure and partial success as normal product states, not exceptional dead ends.

For every long-running or external operation, the UI should show:

- current state;
- who initiated it;
- start time and elapsed time;
- latest successful step;
- failure reason in plain language;
- recommended next action;
- whether retry is safe.

This applies to login, real source connection, real source scan, recording, import, replay, scenario run, evidence capture, and export.

### Accessibility Baseline

The MVP UI should target WCAG 2.2 AA as the baseline for product design.

Accessibility expectations:

- all controls are keyboard reachable;
- focus state is always visible;
- status changes are announced without forcing focus movement;
- error messages identify the problem and suggest a recovery action;
- color is never the only way to communicate state;
- text and control contrast meet AA expectations;
- data tables, forms, dialogs, tabs, and tree views have accessible names and state;
- destructive confirmations are understandable by screen reader users;
- live updates do not make keyboard or screen reader interaction unstable.

### Progressive Disclosure For Complex Tools

The UI should keep common workflows direct while allowing expert depth.

Examples:

- basic connection fields shown first, advanced protocol settings behind an advanced section;
- schema table/tree visible first, selected item details shown in a side panel;
- scenario step list shown first, protocol-specific options shown only after step type and target are selected;
- evidence summary shown first, detailed timelines and raw events available in tabs.

## Primary Users

### QA Engineer

Needs to reproduce bugs, run regression scenarios, simulate faults, and export evidence.

Most important UI needs:

- quick project open;
- scenario run controls;
- visible pass/fail-relevant runtime behavior;
- easy evidence export.

### Edge Device Developer

Needs to develop and debug Edge Device behavior against simulated instruments.

Most important UI needs:

- fast data-source setup;
- live values;
- connected client visibility;
- runtime event history;
- protocol endpoint details.

### Support Or Field Engineer

Needs to reproduce customer conditions from recordings, samples, or imported projects.

Most important UI needs:

- import project;
- clear setup summary;
- replay real behavior;
- collect evidence for handoff.

### Shared Environment Operator

Needs to run team-level simulator setups without accidentally disrupting others.

Most important UI needs:

- project status overview;
- role-aware controls;
- active user visibility later;
- clear warnings before stopping shared runs.

For MVP, the shared QA/developer experience should be the strongest priority, while leaving room for local personal use as a secondary mode.

## Information Architecture

The app should use a stable workbench layout.

### Global Layout

Top bar:

- current project name;
- project switcher;
- environment indicator;
- global run status;
- primary run controls;
- evidence/export shortcut;
- user menu with name, role, and logout.

Left navigation:

- Overview;
- Data Sources;
- Recordings & Samples;
- Scenarios;
- Evidence;
- Settings.

Main content:

- current page title;
- contextual primary action;
- filters or tabs when needed;
- primary work area.

Bottom or side runtime panel:

- recent runtime events;
- health alerts;
- active faults;
- connected clients summary.

The runtime panel should be easy to collapse, but runtime state should never disappear completely.

## MVP Screens

### Login

Purpose:

Let a known user enter the shared simulator environment.

Main content:

- environment or instance name;
- login field;
- password field;
- sign in action;
- clear authentication and server availability errors.

Design notes:

- Keep the screen plain and trustworthy.
- Do not expose simulator controls before login.
- Do not add marketing content to the login screen.

### Project Entry

Purpose:

Help a signed-in user choose where to work.

Main content:

- accessible projects;
- create new project;
- open existing project;
- import project;
- active project status;
- recent team activity;
- user's recent projects.

Design notes:

- Use plain, task-based actions.
- Do not show an empty dashboard before a project is selected or created.
- Avoid long explanatory text.

### Project Overview

Purpose:

Give a clear command center for the current simulator project.

Must show:

- project status;
- running data-sources;
- configured data-sources;
- connected clients;
- active replay or scenario;
- who started active runs or scenarios;
- health alerts;
- latest runtime events;
- recent team activity;
- latest evidence artifact.

Primary actions:

- start all enabled;
- stop all;
- create data-source;
- run scenario;
- export evidence.

The overview should answer four questions immediately:

- What exists?
- What is running?
- Who is connected?
- What recently happened?
- Who is currently operating or editing this project?

### Data Sources List

Purpose:

Let users manage simulated OPC UA and Modbus TCP sources.

Each data-source row or card should show:

- name;
- protocol;
- enabled state;
- running state;
- run initiator when running;
- endpoint;
- schema summary;
- connected clients count;
- active data mode;
- health.

Primary actions:

- create data-source;
- start;
- stop;
- duplicate;
- edit;
- remove.

Rows should be dense and scannable. Avoid oversized cards for the main list.

### Create Data Source Wizard

Purpose:

Guide users through creating a source without forcing them to know every detail up front.

The wizard should be protocol-extensible. OPC UA and Modbus TCP are the first supported protocols, but the interaction model should also work when more protocols are added later.

Use one unified wizard shell for all protocols. Do not create separate top-level creation flows for each protocol. Protocol-specific fields should appear inside shared steps after the user chooses a protocol.

Steps:

1. Choose protocol.
2. Choose source basis.
3. Acquire or define structure.
4. Configure data behavior.
5. Name and simulated endpoint.
6. Review and create.

Protocol step:

- OPC UA;
- Modbus TCP.

Future protocols should appear as additional choices in the same protocol step. The protocol step should use clear cards or rows with protocol name, short description, and availability state.

Source basis step:

- scan real source;
- use recording or sample;
- create manually;
- generate synthetic data.

Scan real source should be the visually recommended path, while the other options remain equally available and clearly explained by their labels.

Acquire or define structure step:

- for scan real source: connection details, test connection, scan preview, and selectable discovered structure;
- for recording or sample: source recording/sample selector and timeline preview;
- for manual creation: schema editor entry point;
- for synthetic generation: generation pattern and value range setup.

Data behavior step:

- record real data after source creation;
- static values;
- synthetic values;
- replay recording or sample;
- scenario-controlled later.

When the user chooses Scan real source, the review step should offer two clear completion actions:

- Create source;
- Create source and record now.

This keeps the wizard simple while making the primary Scan real source -> Record -> Replay flow easy to continue.

Review step should show:

- protocol;
- source basis;
- endpoint;
- schema count;
- initial data behavior;
- whether the source starts immediately.

After creation, the success state should show the next best action based on source basis:

- scan real source: record now or start simulated source;
- recording or sample: start replay;
- manual creation: add values or start source;
- synthetic generation: start source or tune generated values.

### Data Source Detail

Purpose:

Let users understand and control one source.

Recommended tabs:

- Overview;
- Schema;
- Values;
- Clients;
- Events;
- Settings.

Overview tab:

- run state;
- endpoint;
- protocol;
- enabled state;
- active replay/scenario;
- active faults;
- health.

Schema tab:

- tree/table for OPC UA nodes or Modbus registers;
- type;
- address/path;
- current value;
- write permissions if relevant;
- validation status.

The Schema tab should include a full schema editor. Users should be able to change any editable part of the simulated source schema, whether the schema was scanned from a real source, created manually, generated synthetically, or imported from a recording/sample.

Full schema editor capabilities:

- add, edit, duplicate, move, and remove schema items;
- edit protocol-specific identifiers and addressing;
- edit names, labels, descriptions, and grouping;
- edit data types and value constraints;
- edit read/write behavior when supported by the protocol;
- edit initial values and default generated values;
- edit metadata needed to make the simulated source understandable to users;
- validate schema changes before saving;
- show clear errors and warnings inline;
- show unsaved changes;
- support cancel, save, and save as duplicate when useful;
- show who is editing or last edited the schema.

The editor should feel powerful but controlled. Use a split layout where the left side shows the schema tree or table, and the right side shows editable details for the selected item. This keeps large schemas scannable without hiding detailed controls.

Protocol-specific editing should live inside the selected item's details panel. The main editor layout should remain consistent across protocols so future protocols can be added without teaching users a new editing model.

Values tab:

- live value table;
- search/filter;
- manual value override for editable values;
- data generation mode;
- update timestamp.

Clients tab:

- connected Edge Devices or clients;
- connection time;
- client address or identity when available;
- protocol session state.

Events tab:

- source start/stop;
- client connect/disconnect;
- value mode changes;
- replay changes;
- faults;
- errors.

Settings tab:

- name;
- enabled flag;
- endpoint settings;
- startup behavior;
- protocol-specific settings.

### Recordings & Samples

Purpose:

Let users preserve and reuse real or curated data behavior.

Must support:

- list recordings;
- list samples;
- create recording from real source;
- import recording/sample from prepared file;
- export recording/sample when users need to move reusable data independently;
- preview timeline;
- assign recording/sample to replay;
- rename and describe reusable data sets.

Important columns:

- name;
- source;
- type;
- duration or size;
- origin, such as recorded, imported, or generated;
- created date;
- tags or description;
- last used.

Import recording/sample flow:

1. User chooses Import recording/sample.
2. User uploads or selects a prepared file.
3. UI validates the file and shows a readable summary.
4. User maps the import to protocol and data-source when needed.
5. User previews timeline, value count, and any warnings.
6. User names the recording/sample and saves it into the project.

The import flow should be forgiving and explain problems in user terms. If a file cannot be used, the UI should say what is wrong and what the user can do next.

### Scenario Builder

Purpose:

Let users combine replay, synthetic data, timing, ordering, and faults into a meaningful test flow.

MVP approach:

- use a simple ordered step list;
- each step has a type;
- each step has duration or trigger;
- each step can target one or more data-sources;
- show a readable scenario summary before run.

Step types:

- start data-source;
- stop data-source;
- replay recording/sample;
- generate synthetic values;
- apply fault;
- clear fault;
- wait;
- mark event.

The MVP should avoid a complex visual canvas. A structured step list is easier to understand, test, and maintain.

### Scenario Run View

Purpose:

Let users run and observe one scenario.

Must show:

- scenario name;
- run state;
- current step;
- progress;
- timeline of completed and upcoming steps;
- active data-sources;
- active faults;
- connected clients;
- live events.

Primary actions:

- run;
- pause if supported;
- stop;
- rerun;
- export evidence.

### Evidence

Purpose:

Let users review and export what happened during manual or automated simulator runs.

Evidence capture should start automatically for every data-source run and scenario run. Users should not need to remember to start capture manually.

Export remains an explicit user action. After a run completes, the user can review the captured evidence, rename it, add notes if supported, and export it when needed.

Evidence capture states:

- Capturing: the run is still active;
- Ready: the run is complete and evidence can be reviewed;
- Exported: the evidence has been exported;
- Export failed: the export attempt failed.

Evidence list should show:

- run name or generated title;
- project;
- scenario if any;
- initiator;
- start and end time;
- status;
- data-sources involved;
- faults;
- errors;
- export state.

Evidence detail should show:

- run summary;
- initiator and export author;
- value timelines;
- client connection history;
- runtime events;
- faults;
- errors;
- export/download action.

Evidence export should feel like a natural end of a run, not a hidden advanced feature.

Evidence export options in MVP:

- Report: readable evidence report for QA, support, and review;
- Full bundle: complete evidence package for debugging, handoff, and reproduction;
- Value timeline CSV: time-series value data for analysis in external tools.

The export dialog should explain the purpose of each option in plain language. The primary recommended option should be Report unless the user is explicitly preparing a debug handoff.

User-facing import/export actions in MVP:

- Import project;
- Export project;
- Import recording/sample;
- Export recording/sample;
- Export evidence.

The UI should name actions by user intent, not by internal storage format. File format details can appear in dialogs, helper text, or download labels when useful.

### Settings

Purpose:

Keep project and environment settings out of the main workflows.

MVP settings:

- project name and description;
- endpoint visibility;
- import/export project;
- default startup behavior;
- current environment name;
- user and role management for Admin users.

## Runtime State Model In UI

The UI should use consistent state labels.

Data-source state:

- Stopped;
- Starting;
- Running;
- Stopping;
- Error;
- Disabled.

Client state:

- Connected;
- Disconnected;
- Reconnecting, if known.

Scenario state:

- Draft;
- Ready;
- Running;
- Completed;
- Stopped;
- Failed.

Evidence state:

- Capturing;
- Ready;
- Exported;
- Export failed.

Fault state:

- Inactive;
- Scheduled;
- Active;
- Cleared.

## Visual Design Direction

The MVP should use a calm operational interface.

Recommended visual qualities:

- dense but readable layouts;
- restrained colors;
- high contrast for runtime state;
- clear tables and timelines;
- no decorative hero pages;
- no marketing-style cards for core workflows;
- strong empty states with direct actions;
- consistent icons for run, stop, edit, duplicate, export, warning, health.

Color should communicate state:

- green for running/healthy;
- red for error/stopped by failure;
- yellow or amber for warning/fault;
- blue or neutral for informational active work;
- gray for disabled/inactive.

Do not rely on color alone. Pair state color with labels and icons.

## Interaction Patterns

### Global Run Controls

Global controls should be visible on most project screens:

- start all enabled;
- stop all;
- current run status;
- active scenario indicator.

Stopping active simulations should require confirmation when it affects multiple running data-sources.

### Concurrent Editing

When several users interact with the same project, the UI should reduce accidental overwrites and surprise state changes.

Recommended MVP behavior:

- use automatic edit locks for editable objects;
- the first user who starts editing receives the edit lock;
- other users can open the object in read-only mode while it is locked;
- show who is currently editing an object;
- show who last saved an object;
- show a clear message such as "Anna is editing this scenario";
- allow Admin users to release stale locks;
- warn before leaving a page with unsaved changes.

The UI should avoid strict manual checkout in the MVP. Users should not need to click "Take control" before every edit.

Concurrent editing edge cases:

- if the editing user closes the browser or loses connection, the lock should eventually become stale and releasable by Admin;
- if a user loses edit access while editing, the UI should stop accepting saves and explain what changed;
- if an object is deleted while another user is viewing it, the viewer should see a clear unavailable state with a link back to the parent list;
- if runtime state changes while a user edits configuration, the UI should show the new runtime state before save or run actions.

### Destructive Actions

Destructive or disruptive actions require confirmation.

Examples:

- stop all running data-sources;
- stop a scenario started by another user;
- remove a data-source;
- delete a recording, sample, scenario, or evidence artifact;
- import a project in a way that overwrites existing project content.

Confirmation dialogs should explain the impact in user terms, including affected data-sources, connected clients, and active runs when relevant.

High-impact confirmations should name the affected object and impact directly. For example:

- stopping a source should show connected client count;
- stopping a scenario should show who started it and current step;
- deleting a recording/sample should show which scenarios or sources use it;
- importing over existing content should show what will be replaced or duplicated.

### Contextual Actions

Primary page actions should live near the page title:

- Create data-source on Data Sources;
- Create recording on Recordings & Samples;
- Create scenario on Scenarios;
- Export evidence on Evidence detail.

### Tables

Use tables for operational lists:

- data-sources;
- recordings;
- samples;
- evidence;
- clients;
- events.

Tables should support:

- search;
- protocol/source filters where relevant;
- status filters;
- sorting where order affects scanning;
- pagination or virtualized scrolling for large lists;
- visible active filters;
- no-results states that help users adjust filters;
- row actions;
- empty states.

For dense operational tables, avoid hiding critical status in hover-only interactions. Status, health, owner/initiator, and active run indicators should be visible in the row.

### Wizards

Wizards should:

- show progress;
- preserve entered values when moving back;
- validate each step;
- show a final review;
- create in stopped state unless the user explicitly chooses to start immediately.

### Empty States

Empty states should be action-oriented:

- no project: create/open/import project;
- no data-sources: create data-source;
- no recordings: record from real source or use synthetic values;
- no scenarios: create first scenario from existing source;
- no evidence: run a scenario or export after manual run.

Avoid explaining every product concept in empty states. Give the next useful action.

When an empty state is caused by error, permissions, unsupported file type, connection failure, or filters, explain the specific cause and offer a recovery path.

## Edge And Failure States

The MVP UI should explicitly design for these cases.

### Login And Session

- invalid login or password;
- expired session while viewing runtime state;
- expired session while editing with unsaved changes;
- user role changed during an active session;
- server unavailable after the login screen loads.

Expected UI behavior:

- preserve unsaved local form state when possible;
- redirect to login only when necessary;
- explain whether the user can retry, refresh, or contact an Admin;
- never expose project content before authentication.

### Real Source Connection And Scan

- real source unreachable;
- authentication or permission failure;
- slow connection test;
- scan partially succeeds;
- unsupported or unknown data type discovered;
- duplicate or conflicting identifiers discovered;
- very large schema discovered;
- real source disconnects during scan;
- scan result becomes stale before source creation.

Expected UI behavior:

- show step-level progress;
- allow retry without losing entered connection details;
- show partial results with warnings when useful;
- support search, filter, and selection in large scan previews;
- clearly distinguish real source endpoint from simulated endpoint.

### Recording, Import, And Replay

- recording started but real source disconnects;
- recording produces no values;
- recording becomes very large;
- imported file has unsupported format;
- imported file is valid but does not match selected protocol or schema;
- timestamps, time zones, or sampling intervals are inconsistent;
- replay is started with no connected clients;
- replay target source is already running with another data behavior.

Expected UI behavior:

- show recording duration, value count, and last received value;
- allow saving partial recording with a warning when useful;
- validate imports before saving them into the project;
- preview imported timeline and warnings before commit;
- warn when replay will replace or interrupt current data behavior.

### Schema Editing

- editing a running source schema;
- changing an identifier used by recordings, samples, scenarios, or evidence;
- changing data type for an item with existing recorded values;
- deleting a schema item used by a scenario;
- bulk editing many items;
- validation errors after scan or import.

Expected UI behavior:

- warn before schema changes that affect active runs or dependent assets;
- show dependency impact before save;
- allow cancel before committing changes;
- keep validation errors close to the affected item;
- support search and filters for large schemas.

### Runtime And Observability

- runtime state changes because another user starts or stops something;
- client connects late or disconnects during replay;
- active fault affects values or client connectivity;
- event stream reconnects or becomes stale;
- health status is unknown rather than healthy or failed.

Expected UI behavior:

- show last updated time for live panels;
- show reconnecting or stale-data state when live updates are interrupted;
- keep runtime events append-only and easy to filter;
- make active faults visible near affected sources and scenarios.

### Evidence And Export

- evidence is still capturing;
- evidence capture fails while the run continues;
- export fails;
- evidence includes no connected clients;
- evidence is very large;
- evidence contains warnings, partial data, or interrupted replay.

Expected UI behavior:

- allow opening evidence while capture is active;
- show what has been captured so far;
- explain missing or partial sections;
- allow retry for failed exports;
- keep export options understandable and task-based.

## MVP Navigation Flow

### Capture And Replay Real Source Behavior

1. Open app.
2. Sign in.
3. Select or create project.
4. Create data-source.
5. Choose protocol.
6. Choose Scan real source.
7. Enter real source connection details.
8. Test connection.
9. Scan real source structure.
10. Review discovered schema.
11. Create simulated data-source.
12. Record real data behavior.
13. Save recording or sample.
14. Assign replay to simulated data-source.
15. Start simulated data-source.
16. Confirm Edge Device client connects.
17. Observe replayed values, team activity, and events.
18. Export evidence if needed.

### Create And Run Manual Or Synthetic Source

1. Open app.
2. Sign in.
3. Select or create project.
4. Create data-source.
5. Choose protocol.
6. Choose Create manually or Generate synthetic data.
7. Define schema or generation pattern.
8. Review endpoint.
9. Create source.
10. Start source.
11. Confirm Edge Device client connects.
12. Observe live values, team activity, and events.
13. Export evidence if needed.

### Reproduce A Bug From Recording

1. Open project.
2. Go to Recordings & Samples.
3. Select recording.
4. Assign replay to data-source.
5. Start source.
6. Connect Edge Device.
7. Observe runtime behavior.
8. Export evidence.

### Run A Fault Scenario

1. Open project.
2. Go to Scenarios.
3. Create scenario.
4. Add replay or synthetic data steps.
5. Add fault step.
6. Review scenario.
7. Run scenario.
8. Observe current step, faults, clients, and events.
9. Export evidence.

## MVP Scope Boundaries

In MVP UI:

- shared usage is the primary experience;
- login and password are required before project access;
- user identity is shown for important run, scenario, edit, and evidence actions;
- roles are limited to Admin and User;
- scenario builder is a structured step list, not a visual canvas;
- user and environment administration is Admin-only;
- advanced protocol tuning should stay behind settings or advanced sections;
- automated test control should be visible through status/evidence concepts, but detailed API design belongs elsewhere.

## Additional MVP Coverage Decisions

These decisions close design gaps against the product capability contract while keeping technical implementation details out of this UI document.

### Project Lifecycle

The MVP UI should support a clear project lifecycle:

- create project;
- rename project;
- duplicate project;
- import project;
- export project;
- archive project;
- delete project.

Archive should be the safer default for removing a project from active use. Delete is destructive and must require confirmation with impact, including active runs, data-sources, recordings, scenarios, and evidence.

### Automated Test Runs In UI

Runs started from automated tests should appear in the same runtime and evidence surfaces as manual runs.

The UI should show:

- initiator as Automation or a named automation identity when available;
- run source, such as UI or automated test;
- scenario/project/data-source affected;
- readiness, running, stopped, failed, and completed states;
- evidence captured from automated runs.

Detailed API design is out of scope, but automated activity must not be invisible in the UI.

### Local And Shared Modes

The MVP UI is shared-login-first. Local trusted no-login usage remains compatible with the information architecture, but it is not the primary MVP UI mode.

If local mode is enabled later, the UI should show a clear mode indicator and avoid mixing local-only assumptions into shared screens.

### Identity Providers And Roles

The MVP UI uses login/password and Admin/User roles.

External identity providers and expanded roles from the product contract should be treated as future-compatible:

- login screen should not assume local accounts forever;
- user menu should have space for identity/provider metadata later;
- role display should not hard-code only two visual states in a way that prevents viewer/operator/editor/admin later.

### Real Source Credentials

Connection credentials for real source scan must be handled carefully in UI.

The UI should:

- mask sensitive fields;
- clearly show whether credentials are saved, session-only, or not stored;
- avoid showing secrets in summaries, activity, evidence, or exports;
- allow users to clear saved credentials when supported;
- show credential-related errors without exposing secret values.

### Retention And Cleanup

Recordings, samples, and evidence can become large. The MVP UI should include visibility and cleanup affordances.

The UI should show:

- size or approximate size where useful;
- created date and last used date;
- export state;
- dependency warnings before deletion;
- Admin cleanup entry point for old evidence/recordings if storage becomes a concern.

### Activity And Audit Visibility

The Overview activity feed is not enough for every case. MVP should include a fuller activity/history view or at least a routeable filtered activity surface.

It should support filtering by:

- user or automation identity;
- project;
- data-source;
- scenario;
- evidence;
- action type;
- time range.

### Notifications

Use notifications sparingly:

- inline alerts for local, fixable problems;
- toasts for non-blocking success or background completion;
- persistent banners for stale live data, server reconnecting, or environment-wide issues;
- confirmation dialogs for destructive or disruptive actions.

### Responsive And Browser Baseline

The MVP should be desktop-first. Tablet-width layouts should remain usable for observation and basic operation. Phone-width layouts may be read-only or limited unless the team explicitly decides to support full mobile operation.

The UI should not depend on hover-only controls because operational tools are often used on laptops, touchpads, remote desktops, and tablets.

### Protocol-Specific Field Baseline

The wizard and schema editor need a first UI pass for protocol-specific fields.

For OPC UA, the UI should account for:

- endpoint;
- security mode/policy when relevant;
- authentication fields when relevant;
- namespace;
- node id;
- browse name;
- data type;
- access level.

For Modbus TCP, the UI should account for:

- host and port;
- unit id;
- register type;
- address;
- data type;
- scaling or transform when needed;
- read/write behavior.

## Open Questions

No open UI design questions remain in the current MVP decision set.

## Design Reference Principles

The UI design should be checked against established product design practices:

- Nielsen Norman Group usability heuristics: visibility of system status, match with the real world, user control, consistency, error prevention, recognition over recall, efficient use, focused visual design, helpful errors, and contextual help.
- WCAG 2.2: use AA accessibility expectations as the baseline for contrast, focus, keyboard operation, labels, error handling, and status messages.
- Enterprise design system practice: use data tables for dense operational data, contextual empty states, visible filters, batch actions only where helpful, and plain-language error recovery.

Reference links:

- https://www.nngroup.com/articles/ten-usability-heuristics/
- https://www.w3.org/TR/WCAG22/
- https://carbondesignsystem.com/components/data-table/usage/
- https://carbondesignsystem.com/patterns/empty-states-pattern/

## Current Design Decisions

- The MVP UI is a shared Web UI with mandatory login and password.
- MVP roles are Admin and User.
- All signed-in Users can see all projects in the environment.
- The main navigation is organized by user task areas, not implementation modules.
- The primary MVP product flow is Scan real source -> Record -> Replay.
- Manual creation and synthetic data generation are first-class alternative creation paths.
- Data-source creation uses one protocol-extensible wizard with protocol-specific fields inside shared steps.
- The Schema tab includes a full schema editor where users can change any editable part of the simulated source schema.
- The Overview page is the operational command center.
- The Overview page shows team activity and active run ownership.
- Data-source creation uses a guided wizard.
- Scenario creation uses an ordered step list.
- Runtime state remains visible across the app.
- Edge and failure states are first-class UI states, especially for login, scan, recording, import, replay, schema editing, runtime observability, and evidence export.
- WCAG 2.2 AA is the accessibility baseline for UI design.
- Active runs, scenarios, and evidence show their initiator.
- Evidence capture starts automatically for every data-source run and scenario run; export is an explicit user action.
- Evidence export options are Report, Full bundle, and Value timeline CSV.
- Users can import and export recordings/samples independently from full project import/export.
- Destructive actions require confirmation.
- Evidence is treated as a first-class workflow.
- Concurrent editing uses automatic edit locks with read-only access for other users and Admin stale-lock release.
- Project lifecycle includes create, rename, duplicate, import, export, archive, and delete, with archive as the safer removal path.
- Automated test runs are visible in runtime, activity, and evidence UI even though detailed API design is out of scope.
- Local trusted no-login usage is future-compatible, but shared login-first usage is the MVP UI priority.
- External identity providers and expanded roles are future-compatible; MVP UI uses login/password and Admin/User.
- Real source credentials are masked and not exposed in summaries, activity, evidence, or exports.
- Retention and cleanup affordances are required for large recordings, samples, and evidence.
- Activity/audit history should be filterable by actor, object, action type, project, and time range.
- MVP is desktop-first, tablet-usable for observation/basic operation, and phone support can be limited unless explicitly expanded.
