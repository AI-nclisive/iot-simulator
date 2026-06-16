# MVP UI Task Breakdown

This document breaks the MVP UI design into implementation-ready tasks.

Use with:

- `DESIGN.md` for product decisions;
- `UI_PLAN.md` for planning;
- `UI_SCREEN_SPECS.md` for screen-level requirements.

## Task Format

| Field | Meaning |
|---|---|
| ID | Stable task identifier |
| Priority | High, Medium, Low |
| Depends on | Required earlier task |
| Output | Expected artifact or implemented UI surface |
| Acceptance | Minimum behavior for task completion |

## Foundation Tasks

| ID | Priority | Task | Depends on | Output | Acceptance |
|---|---|---|---|---|---|
| UI-001 | High | Define app shell | None | Top bar, side nav, runtime panel spec/component | Project context, nav, user menu, runtime state visible |
| UI-002 | High | Define shared UI states | UI-001 | Status, health, stale, locked, loading, empty, error patterns | States reusable across screens and not color-only |
| UI-003 | High | Define table pattern | UI-001 | Table behavior spec/component | Search/filter/sort/no-results/row actions supported |
| UI-004 | High | Define confirmation pattern | UI-001 | Confirmation dialog pattern | Destructive actions explain impact and affected objects |
| UI-005 | High | Define edit lock pattern | UI-001 | Locked/read-only behavior | Lock owner visible; Admin stale unlock path defined |

## Access And Project Entry

| ID | Priority | Task | Depends on | Output | Acceptance |
|---|---|---|---|---|---|
| UI-010 | High | Design login screen | UI-001 | Login screen spec/mock | Handles loading, invalid credentials, server unavailable, session expired |
| UI-011 | High | Design project entry | UI-001, UI-003 | Project list/create/import screen | User can see projects, running status, recent activity |
| UI-012 | Medium | Design project import dialog | UI-004, UI-011 | Import flow | Import progress/failure/overwrite impact visible |
| UI-013 | Medium | Design project lifecycle actions | UI-004, UI-011 | Rename/duplicate/archive/delete behavior | Archive is safe default; delete shows active run and dependency impact |
| UI-014 | Medium | Design project export dialog | UI-011 | Export flow | Export scope and completion/failure states are clear |

## Operational Foundation

| ID | Priority | Task | Depends on | Output | Acceptance |
|---|---|---|---|---|---|
| UI-020 | High | Design Project Overview | UI-001, UI-002, UI-003 | Overview screen spec/mock | Shows running state, initiator, clients, alerts, activity, next actions |
| UI-021 | High | Design Data Sources List | UI-003, UI-004 | Data sources list spec/mock | Row shows status, endpoint, clients, health, initiator |
| UI-022 | High | Design runtime panel | UI-001, UI-002 | Runtime panel spec/component | Recent events, clients, faults, stale state visible |
| UI-023 | Medium | Design team activity feed | UI-001 | Activity feed spec/component | Shows user, action, object, timestamp, link target |
| UI-024 | Medium | Design activity/audit history view | UI-003, UI-023 | Filterable history view | User can filter by actor, object, action type, project, and time range |
| UI-025 | Medium | Design automated run visibility | UI-020, UI-022 | Automation run indicators | Automated runs show initiator/source and appear in runtime/evidence surfaces |

## Data Source Creation And Capture

| ID | Priority | Task | Depends on | Output | Acceptance |
|---|---|---|---|---|---|
| UI-030 | High | Design create data-source wizard shell | UI-001, UI-002 | Wizard stepper and shared behavior | Back/next/cancel/review preserve input and validation |
| UI-031 | High | Design protocol selection step | UI-030 | Protocol step spec/mock | OPC UA, Modbus TCP, future protocol availability shown |
| UI-032 | High | Design source basis step | UI-030 | Source basis step spec/mock | Scan real source recommended; other paths first-class |
| UI-033 | High | Design scan real source branch | UI-030 | Connection/test/scan/review flow | Retry, partial scan, large schema, unknown type handled |
| UI-034 | High | Design recording flow | UI-033 | Recording spec/mock | Duration, value count, disconnect, partial save handled |
| UI-035 | High | Design replay flow | UI-034 | Replay spec/mock | Target compatibility, no clients, target running, complete states handled |
| UI-036 | Medium | Design manual source branch | UI-030 | Manual creation path | Opens schema editor entry point and validates required schema |
| UI-037 | Medium | Design synthetic source branch | UI-030 | Synthetic setup path | Pattern/range/update/deterministic options represented |
| UI-038 | Medium | Design recording/sample branch | UI-030 | Existing data selector | Compatibility and timeline preview visible |
| UI-039 | High | Define OPC UA and Modbus field baseline | UI-031 | Protocol field spec | Connection and schema fields for OPC UA/Modbus are represented consistently |
| UI-039A | High | Design real-source credentials UI | UI-033, UI-039 | Credential field behavior | Secrets are masked and storage/session-only status is clear |

## Data Source Detail And Schema

| ID | Priority | Task | Depends on | Output | Acceptance |
|---|---|---|---|---|---|
| UI-040 | High | Design Data Source Detail shell | UI-021 | Tabbed detail screen | Overview, Schema, Values, Clients, Events, Settings available |
| UI-041 | High | Design source overview tab | UI-040 | Overview tab spec/mock | State, endpoint, health, active behavior, faults visible |
| UI-042 | High | Design full schema editor | UI-040, UI-005 | Schema editor spec/mock | Tree/table, details panel, validation, unsaved changes, locks |
| UI-043 | High | Design schema dependency warnings | UI-042, UI-004 | Dependency warning behavior | Impact shown for used identifiers/items/type changes |
| UI-044 | Medium | Design values tab | UI-040 | Values tab spec/mock | Live values, mode, timestamps, stale state, overrides where allowed |
| UI-045 | Medium | Design clients tab | UI-040 | Clients tab spec/mock | Connected clients and session state visible |
| UI-046 | Medium | Design events tab | UI-040, UI-022 | Source events view | Start/stop/connect/replay/fault/error events filterable |
| UI-047 | Medium | Design settings tab | UI-040 | Settings tab spec/mock | Name, endpoint, startup, protocol settings with validation |

## Reusable Data

| ID | Priority | Task | Depends on | Output | Acceptance |
|---|---|---|---|---|---|
| UI-050 | Medium | Design Recordings & Samples list | UI-003 | List spec/mock | Name, source, type, origin, duration, tags, last used visible |
| UI-051 | Medium | Design recording/sample import | UI-050 | Import dialog/flow | Validate, map, preview, save; unsupported file handled |
| UI-052 | Medium | Design recording/sample preview | UI-050 | Timeline preview component | Timeline, value count, warnings visible |
| UI-053 | Medium | Design recording/sample export | UI-050 | Export action/dialog | User can export data independently from project |
| UI-054 | Medium | Design assign to replay action | UI-050, UI-035 | Assign flow | Target compatibility and replacement impact shown |

## Scenarios And Faults

| ID | Priority | Task | Depends on | Output | Acceptance |
|---|---|---|---|---|---|
| UI-060 | Medium | Design scenarios list | UI-003 | Scenario list spec/mock | State, last run, owner/editor, actions visible |
| UI-061 | Medium | Design scenario builder shell | UI-005, UI-060 | Builder screen spec/mock | Step list, details panel, validation panel |
| UI-062 | Medium | Design scenario step editor | UI-061 | Step editor spec/mock | Supports start/stop/replay/synthetic/fault/wait/mark event |
| UI-063 | Medium | Design fault configuration | UI-062 | Fault setup spec/mock | Fault target, timing, parameters, clear behavior visible |
| UI-064 | Medium | Design scenario validation | UI-061 | Validation behavior | Run disabled until errors resolved with clear reasons |
| UI-065 | Medium | Design scenario run view | UI-022, UI-061 | Run view spec/mock | Current step, initiator, clients, faults, events, evidence state visible |

## Evidence

| ID | Priority | Task | Depends on | Output | Acceptance |
|---|---|---|---|---|---|
| UI-070 | Medium | Design evidence list | UI-003 | Evidence list spec/mock | Runs searchable by initiator, project, scenario, status |
| UI-071 | Medium | Design evidence detail | UI-070 | Evidence detail spec/mock | Summary, timeline, clients, faults/errors, partial data states |
| UI-072 | Medium | Design evidence export dialog | UI-071 | Export dialog spec/mock | Report, Full bundle, Value timeline CSV explained |
| UI-073 | Medium | Design export failure recovery | UI-072 | Error/retry states | Failed export can be retried with clear reason |
| UI-074 | Medium | Design evidence/recording retention UI | UI-050, UI-070 | Cleanup/archive behavior | Size, age, last used, dependency impact, and cleanup failure states visible |

## Admin And Settings

| ID | Priority | Task | Depends on | Output | Acceptance |
|---|---|---|---|---|---|
| UI-080 | Medium | Design settings screen | UI-001 | Settings spec/mock | Project settings and environment info separated |
| UI-081 | Medium | Design Admin users list | UI-001, UI-003 | Admin users screen | Admin sees users, roles, status, last activity |
| UI-082 | Medium | Design create/edit user flows | UI-081 | User form spec/mock | Login/name/role/status validation and save states |
| UI-083 | Medium | Design role change behavior | UI-081 | Role-change interaction | Role changes are confirmed and visible in activity |

## Visual And Accessibility Pass

| ID | Priority | Task | Depends on | Output | Acceptance |
|---|---|---|---|---|---|
| UI-090 | Medium | Define visual system baseline | UI-001..UI-083 | UI style guide | Status colors, badges, typography, spacing, forms, dialogs |
| UI-091 | Medium | Accessibility review | UI-001..UI-090 | Accessibility checklist | Keyboard, focus, labels, status messages, contrast checked |
| UI-092 | Medium | Edge-state review | UI-001..UI-090 | Edge-case checklist | Empty/loading/error/stale/locked/partial states covered |
| UI-093 | Low | Prototype review | UI-020, UI-030, UI-040 | Clickable prototype or walkthrough | Primary workflows can be reviewed end to end |
| UI-094 | Medium | Define responsive/browser baseline | UI-001..UI-090 | Responsive support decision | Desktop-first, tablet usable, phone limitations documented |
| UI-095 | Medium | Define notification pattern | UI-001, UI-002 | Toast/banner/inline alert rules | Success, warning, error, stale, reconnecting feedback are consistent |

## Recommended Build Order

1. UI-001 to UI-005: foundation patterns.
2. UI-010 to UI-014: access and project entry.
3. UI-020 to UI-025: operational foundation.
4. UI-030 to UI-039A: primary Scan -> Record -> Replay creation flow.
5. UI-040 to UI-047: data-source detail and schema editing.
6. UI-050 to UI-054: reusable recordings/samples.
7. UI-060 to UI-065: scenarios and faults.
8. UI-070 to UI-074: evidence and retention.
9. UI-080 to UI-083: admin/settings.
10. UI-090 to UI-095: consistency, accessibility, responsive behavior, and review.
