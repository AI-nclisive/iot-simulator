# MVP UI Screen Specifications

This document turns the UI design direction into screen-level specs for development.

Use with `DESIGN.md`, `UI_PLAN.md`, and `UI_TASKS.md`.

## Common Rules

| Rule | Requirement |
|---|---|
| App shell | Top bar, left navigation, main content, collapsible runtime panel |
| Runtime state | Visible on relevant screens with last updated time |
| Shared actions | Show initiator/owner for runs, scenarios, edits, evidence |
| Long actions | Show progress, elapsed time, safe retry/cancel state |
| Errors | Explain cause and next action in user terms |
| Tables | Search, filter, sort, visible active filters, no-results state |
| Accessibility | Keyboard reachable, visible focus, no color-only status |
| Locks | Locked objects open read-only for other users |

## Screen Specs

| Screen / Flow | User Goal | Primary UI | Required States | Key Acceptance Criteria |
|---|---|---|---|---|
| Login | Enter shared environment | Environment name, login, password, sign in | empty, loading, invalid credentials, server unavailable, session expired | No project content before auth; errors are clear; keyboard sign-in works |
| Project Entry | Choose where to work | Project list, create project, import project, recent activity | no projects, loading, import progress, import failed, project unavailable | User sees active projects and whether anything is running |
| Project Overview | Understand project state | Summary, primary actions, active runs, alerts, source summary, team activity | empty, ready, running, warning, error, stale data | User can answer what is running, who started it, who is connected, what is unhealthy |
| Data Sources List | Manage simulated sources | Filters, source table, runtime summary, row actions | empty, loading, running, error, locked, large list | Status, endpoint, clients, initiator, health visible in row |
| Create Data Source Wizard | Create source through one extensible flow | Protocol, source basis, structure, behavior, endpoint, review | validation error, connection test, partial scan, incompatible sample, review ready | Future protocols fit same wizard; input preserved when moving back |
| Scan Real Source | Capture structure from real device | Connection form, test, scan progress, schema preview, create source | unreachable, auth failure, partial scan, large schema, unknown data type | Real endpoint and simulated endpoint are clearly distinct |
| Recording Flow | Capture real behavior over time | Source summary, start/stop, duration, value count, timeline, save metadata | ready, recording, no values, disconnected, partial save, save ready | User can save useful partial recording with warning |
| Replay Flow | Replay recording/sample through simulated source | Recording selector, target source, compatibility, progress, clients, events | no clients, target running, replay running, complete, failed | Replay impact is clear before replacing current behavior |
| Data Source Detail | Inspect and operate one source | Overview, Schema, Values, Clients, Events, Settings tabs | stopped, running, error, stale, locked, read-only | Source state, endpoint, health, active behavior always visible |
| Full Schema Editor | Edit any editable schema part | Toolbar, tree/table, details panel, validation, unsaved changes | empty, loading, invalid, running-source warning, dependency warning, locked | User can edit identifiers, types, values, metadata, read/write behavior where supported |
| Recordings & Samples | Manage reusable data | List, filters, import, export, preview, assign to replay | empty, loading, import validation, incompatible file, no-results | Prepared files can be imported and previewed before save |
| Scenario Builder | Build repeatable test flow | Ordered step list, details panel, validation, save/run | draft, invalid, ready, locked | Run disabled until validation passes with visible reasons |
| Scenario Run View | Observe active scenario | Run summary, step timeline, sources, clients, faults, events | running, stopped, failed, completed, stale | Current step, initiator, faults, clients, evidence state visible |
| Evidence List | Find captured evidence | Evidence table, filters, status, export state | empty, capturing, ready, exported, export failed, no-results | User can find runs by initiator, project, scenario, status |
| Evidence Detail | Review and export run evidence | Summary, timeline, clients, faults/errors, export options | capturing, ready, partial, export failed, large evidence | Export options are Report, Full bundle, Value timeline CSV |
| Settings | Manage project/environment settings | Project details, import/export project, defaults, environment info | loading, saved, validation error, permission denied | Admin-only controls hidden or disabled with reason |
| Admin UI | Manage users and roles | Users list, create/edit user, role assignment, status | empty, loading, validation error, role changed, permission denied | Admin can manage users; User cannot access admin actions |
| Project Lifecycle | Manage project existence and reuse | Rename, duplicate, archive, delete, export | active, archived, deleting blocked, export ready, dependency warning | Archive is safer default; delete explains active run and dependency impact |
| Activity / Audit View | Inspect team and automation history | Filters, event list, linked objects | empty, loading, filtered, stale, export unavailable | User can filter by actor, action, object, project, time |
| Automated Runs Visibility | Understand automated test activity | Automation initiator, source, run status, evidence | queued, running, failed, completed, stopped | Automated runs are visible wherever manual runs are visible |
| Credential Handling | Connect to real sources safely | Masked fields, save/session-only indicator, clear action | missing, invalid, saved, session-only, permission failure | Secrets never appear in summaries, activity, evidence, or exports |
| Retention / Cleanup | Manage large reusable artifacts | Size, age, last used, cleanup/archive actions | normal, large, old, dependency warning, cleanup failed | Users understand storage impact and deletion dependencies |
| Notifications | Communicate transient and persistent issues | Inline alert, toast, banner, confirmation | success, warning, error, reconnecting, stale | Blocking vs non-blocking feedback is visually distinct |

## Important Screen Details

### Project Overview First Viewport

| Area | Must Show |
|---|---|
| Project summary | Name, health, global state, last updated |
| Primary actions | Create data-source, Run scenario, Start all enabled, Stop all |
| Active runs | Data-source/scenario, initiator, elapsed time, clients |
| Alerts | Health issues, active faults, stale updates |
| Data-source summary | Counts by protocol and state |
| Team activity | Recent starts, stops, edits, imports, exports |

### Create Data Source Wizard Steps

| Step | Purpose |
|---|---|
| Choose protocol | OPC UA, Modbus TCP, future protocols |
| Choose source basis | Scan real source, use recording/sample, create manually, generate synthetic |
| Acquire or define structure | Scan, select data, edit schema, or define generation |
| Configure data behavior | Record, replay, static, synthetic, scenario-controlled |
| Name and simulated endpoint | Human name and connection target |
| Review and create | Confirm settings and choose next action |

### Protocol-Specific Field Baseline

| Protocol | Fields To Represent |
|---|---|
| OPC UA | Endpoint, security mode/policy, authentication, namespace, node id, browse name, data type, access level |
| Modbus TCP | Host, port, unit id, register type, address, data type, scaling/transform, read/write behavior |

### Schema Editor Layout

| Region | Content |
|---|---|
| Toolbar | Add, duplicate, remove, move, validate, save, cancel |
| Left panel | Schema tree/table with search and filters |
| Right panel | Editable details for selected item |
| Status area | Unsaved changes, validation summary, lock owner |

### Evidence Export Options

| Option | Purpose |
|---|---|
| Report | Human-readable QA/support review |
| Full bundle | Debug/support handoff |
| Value timeline CSV | External value analysis |

## Definition Of Done

Each screen spec is complete when it defines:

| Required Item | Done |
|---|---|
| User goal | [ ] |
| Primary and secondary actions | [ ] |
| Main layout | [ ] |
| Data shown | [ ] |
| Empty/loading/error states | [ ] |
| Permission behavior | [ ] |
| Lock/stale-data behavior where relevant | [ ] |
| Destructive confirmations where relevant | [ ] |
| Accessibility notes | [ ] |
