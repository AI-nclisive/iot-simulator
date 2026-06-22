# UI Task Breakdown

This file turns the UI design into implementation-ready work.

Readable rule: tasks are grouped by delivery stage first, then by area. Each task
has an ID, dependency hint, expected output, and done condition.

## Priority Model

P0: required for the first usable release slice.

P1: required for production-usable breadth after P0.

P2: advanced shared/team workflows or later enhancements.

## Quick Index

P0:

- Foundation
- Project And Runtime Foundation
- Primary Scan -> Record -> Replay Flow
- Data Source Detail
- Evidence
- P0 Review

P1:

- Shared Access And Project Lifecycle
- Operational Breadth
- Creation And Reuse
- Admin, Retention, And Review

P2:

- Activity And Identity
- Scenarios And Faults
- Build Order

## P0 - First Usable Release Slice

### Foundation

- `UI-001` Define app shell
  - Depends on: none.
  - Output: top bar, side nav, runtime panel pattern.
  - Done when: project context, navigation, user/local mode area, and runtime
    state are visible.

- `UI-002` Define shared UI states
  - Depends on: `UI-001`.
  - Output: status, health, stale, locked, loading, empty, and error patterns.
  - Done when: states are reusable and never rely on color alone.

- `UI-003` Define table pattern
  - Depends on: `UI-001`.
  - Output: table behavior pattern.
  - Done when: search, filter, sort, row actions, no-results state, and active
    filters are supported.

- `UI-004` Define confirmation pattern
  - Depends on: `UI-001`.
  - Output: destructive/disruptive confirmation pattern.
  - Done when: impact and affected objects are clear before confirmation.

- `UI-006` Define shared-mode role pattern
  - Depends on: `UI-001`.
  - Output: Admin/User permission behavior.
  - Done when: shared-mode User can observe and start data-sources only, all
    other shared-mode mutations are Admin-only, and trusted local can expose
    full-control behavior.

- `UI-007` Define deployment mode behavior
  - Depends on: `UI-001`, `UI-006`.
  - Output: trusted local and shared team mode rules.
  - Done when: P0 trusted local can skip login and P1 shared mode can introduce
    auth and Admin/User permissions without redesign.

### Project And Runtime Foundation

- `UI-011` Design project entry
  - Depends on: `UI-001`, `UI-003`, `UI-006`.
  - Output: project list/create/import entry screen.
  - Done when: local users or signed-in shared users can see projects, running
    status, and recent activity; create/import is Admin-only in shared mode.

- `UI-020` Design Project Overview
  - Depends on: `UI-001`, `UI-002`, `UI-003`, `UI-006`.
  - Output: overview screen spec/mock.
  - Done when: running state, initiator, clients, alerts, activity, and
    role-aware actions are visible.

- `UI-021` Design Data Sources List
  - Depends on: `UI-003`, `UI-004`, `UI-006`.
  - Output: data-sources list.
  - Done when: each row shows status, endpoint, clients, health, initiator, and
    User can start only.

- `UI-022` Design runtime panel
  - Depends on: `UI-001`, `UI-002`.
  - Output: runtime panel pattern.
  - Done when: recent runtime events, clients, faults, health, and stale state
    are visible.

### Primary Scan -> Record -> Replay Flow

- `UI-030` Design Create Data Source wizard shell
  - Depends on: `UI-001`, `UI-002`, `UI-006`.
  - Output: wizard stepper and shared behavior.
  - Done when: back/next/cancel/review preserve input and validation; wizard is
    Admin-only in shared mode.

- `UI-031` Design protocol selection step
  - Depends on: `UI-030`.
  - Output: protocol selection step.
  - Done when: OPC UA, Modbus TCP, and future protocol availability fit one
    shared pattern.

- `UI-032` Design source basis step
  - Depends on: `UI-030`.
  - Output: source basis step.
  - Done when: Scan real source is recommended, while recording/sample, manual,
    and synthetic paths remain clear and first-class.

- `UI-033` Design scan real source branch
  - Depends on: `UI-030`.
  - Output: connection, test, scan, and review flow.
  - Done when: retry, partial scan, large schema, and unknown type states are
    handled.

- `UI-034` Design recording flow
  - Depends on: `UI-033`.
  - Output: recording interaction.
  - Done when: duration, value count, disconnect, no-values, and partial-save
    states are clear.

- `UI-035` Design replay flow
  - Depends on: `UI-034`.
  - Output: replay interaction.
  - Done when: target compatibility, no clients, target already running, replay
    progress, and completion/failure states are clear.

- `UI-039` Define OPC UA and Modbus field baseline
  - Depends on: `UI-031`.
  - Output: protocol field baseline.
  - Done when: connection and schema fields for OPC UA and Modbus are represented
    consistently.

- `UI-039A` Design real-source credentials UI
  - Depends on: `UI-033`, `UI-039`.
  - Output: credential field behavior.
  - Done when: secrets are masked and storage/session-only status is clear.

### Data Source Detail

- `UI-040` Design Data Source Detail shell
  - Depends on: `UI-021`.
  - Output: tabbed detail screen.
  - Done when: Overview, Schema, Values, Clients, Events, and Settings are
    available.

- `UI-041` Design source overview tab
  - Depends on: `UI-040`.
  - Output: source overview tab.
  - Done when: state, endpoint, health, active behavior, faults, and primary
    actions are visible.

- `UI-044` Design values tab
  - Depends on: `UI-040`.
  - Output: values tab.
  - Done when: live values, mode, timestamps, stale state, and allowed overrides
    are clear.

- `UI-047` Design settings tab
  - Depends on: `UI-040`.
  - Output: settings tab.
  - Done when: name, endpoint, startup behavior, protocol settings, and
    validation are represented.

- `UI-054` Design assign-to-replay action
  - Depends on: `UI-034`, `UI-035`.
  - Output: assign replay flow.
  - Done when: target compatibility and replacement impact are shown.

### Evidence

- `UI-070` Design evidence list
  - Depends on: `UI-003`, `UI-006`.
  - Output: evidence list.
  - Done when: runs are searchable by initiator, project, scenario, and status;
    export is Admin-only in shared mode.

- `UI-071` Design evidence detail
  - Depends on: `UI-070`.
  - Output: evidence detail.
  - Done when: summary, timeline, clients, faults/errors, partial states, and
    captured data are visible.

- `UI-072` Design evidence export dialog
  - Depends on: `UI-071`.
  - Output: export dialog.
  - Done when: Report, Full bundle, and Value timeline CSV are explained, and
    secrets/private keys are excluded.

- `UI-073` Design export failure recovery
  - Depends on: `UI-072`.
  - Output: export error/retry states.
  - Done when: failed export can be retried with a clear reason.

### P0 Review

- `UI-090` Define visual system baseline
  - Depends on: `UI-001`, `UI-002`, `UI-003`, `UI-004`.
  - Output: style guide.
  - Done when: approved stack usage, status colors, badges, typography, spacing,
    forms, tables, and dialogs are defined.

- `UI-091` Accessibility review
  - Depends on: `UI-001`, `UI-002`, `UI-090`.
  - Output: accessibility checklist.
  - Done when: keyboard, focus, labels, status messages, and contrast are checked.

- `UI-092` Edge-state review
  - Depends on: `UI-001`, `UI-002`, `UI-090`.
  - Output: edge-case checklist.
  - Done when: empty, loading, error, stale, locked, partial, and permission
    states are covered.

## P1 - Production-Usable Breadth

### Shared Access And Project Lifecycle

- `UI-005` Define edit lock pattern
  - Depends on: `UI-001`.
  - Output: locked/read-only behavior.
  - Done when: lock owner is visible and Admin stale unlock is defined.

- `UI-010` Design login screen
  - Depends on: `UI-001`, `UI-007`.
  - Output: P1 shared-mode login screen.
  - Done when: loading, invalid credentials, server unavailable, and session
    expired states are handled.

- `UI-012` Design project import dialog
  - Depends on: `UI-004`, `UI-011`.
  - Output: import flow.
  - Done when: progress, failure, overwrite impact, and version compatibility are
    visible.

- `UI-013` Design project lifecycle actions
  - Depends on: `UI-004`, `UI-006`, `UI-011`.
  - Output: rename, duplicate, archive, delete behavior.
  - Done when: archive is the safe default and delete explains active-run and
    dependency impact.

- `UI-014` Design project export dialog
  - Depends on: `UI-006`, `UI-011`.
  - Output: export flow.
  - Done when: scope, completion, failure, and secret exclusion are clear.

### Operational Breadth

- `UI-025` Design automated run visibility
  - Depends on: `UI-020`, `UI-022`.
  - Output: automation indicators.
  - Done when: automated runs show initiator/source and appear in runtime,
    activity, and evidence surfaces.

- `UI-045` Design clients tab
  - Depends on: `UI-040`.
  - Output: clients tab.
  - Done when: connected clients and session state are visible.

- `UI-046` Design events tab
  - Depends on: `UI-040`, `UI-022`.
  - Output: runtime events view.
  - Done when: start/stop/connect/replay/fault/error events are filterable.

### Creation And Reuse

- `UI-036` Design manual source branch
  - Depends on: `UI-030`.
  - Output: manual creation path.
  - Done when: schema editor entry point and required-schema validation are clear.

- `UI-037` Design synthetic source branch
  - Depends on: `UI-030`.
  - Output: synthetic setup path.
  - Done when: pattern, range, update, and deterministic options are represented.

- `UI-038` Design recording/sample branch
  - Depends on: `UI-030`.
  - Output: existing data selector.
  - Done when: compatibility and timeline preview are visible.

- `UI-042` Design full schema editor
  - Depends on: `UI-040`, `UI-005`.
  - Output: schema editor.
  - Done when: tree/table, details panel, validation, unsaved changes, and locks
    are represented.

- `UI-043` Design schema dependency warnings
  - Depends on: `UI-042`, `UI-004`.
  - Output: dependency warning behavior.
  - Done when: impact is shown for used identifiers, items, and type changes.

- `UI-050` Design Recordings & Samples list
  - Depends on: `UI-003`.
  - Output: reusable data list.
  - Done when: name, source, type, origin, duration, tags, and last used are
    visible.

- `UI-051` Design recording/sample import
  - Depends on: `UI-050`.
  - Output: import flow.
  - Done when: version/format validation, mapping, preview, unsupported file, and
    newer-than-supported file states are handled safely.

- `UI-052` Design recording/sample preview
  - Depends on: `UI-050`.
  - Output: timeline preview.
  - Done when: timeline, value count, and warnings are visible.

- `UI-053` Design recording/sample export
  - Depends on: `UI-006`, `UI-050`.
  - Output: export action/dialog.
  - Done when: Admin can export reusable data independently from project export.

- `UI-055` Design deterministic run settings
  - Depends on: `UI-035`, `UI-037`.
  - Output: deterministic replay/synthetic controls.
  - Done when: seed/preset, timing mode, replay behavior, repeat-run summary,
    evidence traceability, and client-delivery caveat are represented.

### Admin, Retention, And Review

- `UI-074` Design evidence/recording retention UI
  - Depends on: `UI-050`, `UI-070`.
  - Output: cleanup/archive behavior.
  - Done when: size, age, last used, dependency impact, and cleanup failure states
    are visible.

- `UI-080` Design settings screen
  - Depends on: `UI-001`, `UI-006`.
  - Output: settings screen.
  - Done when: project settings and environment info are separated and mutation
    is Admin-only in shared mode.

- `UI-081` Design Admin users list
  - Depends on: `UI-001`, `UI-003`, `UI-006`.
  - Output: Admin users/access screen.
  - Done when: Admin sees users, roles, status, and last activity.

- `UI-082` Design access/user management flows
  - Depends on: `UI-081`.
  - Output: user/access form.
  - Done when: known identity, name, role, status validation, and save states are
    clear; product-owned password management is not assumed.

- `UI-083` Design role change behavior
  - Depends on: `UI-081`.
  - Output: role-change interaction.
  - Done when: role changes are confirmed and visible in activity.

- `UI-093` Prototype review
  - Depends on: `UI-020`, `UI-030`, `UI-040`.
  - Output: clickable prototype or walkthrough.
  - Done when: primary workflows can be reviewed end to end.

- `UI-094` Define responsive/browser/platform baseline
  - Depends on: `UI-001`, `UI-090`.
  - Output: responsive and platform support decision.
  - Done when: desktop-first, tablet usable, phone limitations, and Linux/Windows/macOS
    browser behavior are considered.

- `UI-095` Define notification pattern
  - Depends on: `UI-001`, `UI-002`.
  - Output: toast/banner/inline alert rules.
  - Done when: success, warning, error, stale, and reconnecting feedback are
    consistent.

## P2 - Advanced Shared Workflows

### Activity And Identity

- `UI-023` Design team activity feed
  - Depends on: `UI-001`.
  - Output: activity feed component.
  - Done when: user, action, object, timestamp, and link target are visible.

- `UI-024` Design activity/audit history view
  - Depends on: `UI-003`, `UI-023`.
  - Output: filterable history view.
  - Done when: users can filter by actor, object, action type, project, and time
    range.

- `UI-084` Design identity provider and expanded-role compatibility
  - Depends on: `UI-010`, `UI-081`.
  - Output: future compatibility rules.
  - Done when: login, user menu, and role display can support provider metadata
    and viewer/operator/editor/admin without redesign.

### Scenarios And Faults

- `UI-060` Design scenarios list
  - Depends on: `UI-003`, `UI-006`.
  - Output: scenario list.
  - Done when: state, last run, owner/editor, and role-aware actions are visible.

- `UI-061` Design scenario builder shell
  - Depends on: `UI-005`, `UI-006`, `UI-060`.
  - Output: scenario builder shell.
  - Done when: Admin-only editor has step list, details panel, and validation
    panel.

- `UI-062` Design scenario step editor
  - Depends on: `UI-061`.
  - Output: step editor.
  - Done when: start, stop, replay, synthetic, fault, wait, and mark-event steps
    are supported.

- `UI-063` Design fault configuration
  - Depends on: `UI-062`.
  - Output: fault setup.
  - Done when: target, timing, parameters, and clear behavior are visible.

- `UI-064` Design scenario validation
  - Depends on: `UI-061`.
  - Output: validation behavior.
  - Done when: run is disabled until errors are resolved with clear reasons.

- `UI-065` Design scenario run view
  - Depends on: `UI-022`, `UI-061`.
  - Output: scenario run screen.
  - Done when: current step, initiator, clients, faults, events, and evidence
    state are visible.

## Build Order

Recommended order:

1. Complete all P0 foundation tasks.
2. Complete P0 project/runtime screens.
3. Complete P0 Scan -> Record -> Replay.
4. Complete P0 evidence and review tasks.
5. Move to P1 shared access, reuse, admin, deterministic settings, and platform
   polish.
6. Move to P2 scenarios, faults, audit, and identity expansion.

Do not start P1 or P2 work if it blocks the P0 Scan -> Record -> Replay slice.
