# MVP UI Design Plan

This document explains how the UI design work should be organized for the IoT Data Source Simulator MVP.

Detailed UI decisions live in `DESIGN.md`.

Development handoff artifacts:

| File | Purpose |
|---|---|
| `DESIGN.md` | Product-level UI decisions and UX principles |
| `UI_SCREEN_SPECS.md` | Screen-level requirements, states, and acceptance criteria |
| `UI_TASKS.md` | Implementation-ready UI task breakdown |

## 1. Why This Plan Exists

| Goal | Why It Matters |
|---|---|
| Align the team | Everyone understands the same MVP, users, and workflows |
| Control MVP scope | The team can see what is included now and what waits |
| Reduce redesign | Core flows, states, and edge cases are decided before mockups |
| Guide implementation | Engineers can see expected screens, states, and interaction rules |
| Support presentation | Product direction is easy to explain to stakeholders |

## 2. Product Goal

| Topic | Decision |
|---|---|
| Product type | Shared operational Web UI |
| Primary value | Capture real industrial device behavior and replay it safely |
| MVP story | Scan real source -> Record -> Replay -> Observe -> Export evidence |
| Usage context | QA, development, support, and shared team debugging |
| UI character | Practical workbench, not a marketing dashboard |

## 3. Users And Access

| User / Role | What They Need From UI |
|---|---|
| QA engineer | Reproduce bugs, run scenarios, simulate faults, export evidence |
| Edge Device developer | Create sources, inspect live values, debug clients and events |
| Support / field engineer | Import projects/data, replay real behavior, collect handoff evidence |
| Admin | Manage users, roles, and environment-level settings |
| User | Work with all projects, sources, recordings, scenarios, and evidence |

Access decisions:

| Decision | MVP Choice |
|---|---|
| Login | Required |
| Roles | Admin and User |
| Project visibility | All signed-in Users see all projects |
| Collaboration | Automatic edit locks |
| Locked object behavior | Other users see read-only view |

## 4. MVP Workflows

These are the workflows the UI must make understandable and reliable.

| Priority | Workflow | Purpose |
|---|---|---|
| High | Scan real source -> Record -> Replay | Main product differentiator |
| High | Create manual data-source | Work when real devices are unavailable |
| High | Generate synthetic data | Create deterministic test and boundary data |
| High | Import recording/sample | Use prepared data files |
| High | Observe runtime state | Understand what is running, connected, unhealthy, or stale |
| High | Export evidence | Preserve what happened during a run |
| High | Manage project lifecycle | Rename, duplicate, archive, delete, import, export |
| High | Show automated runs | Make automated test activity visible in runtime/evidence UI |
| Medium | Build scenario | Combine replay, synthetic data, timing, and faults |
| Medium | Admin user management | Support production-usable shared environment |
| Medium | Clean up large artifacts | Manage retention for evidence, recordings, and samples |

## 5. Information Architecture

| Area | Purpose |
|---|---|
| Project Entry | Select, create, or import project |
| Project Overview | Main command center for runtime state |
| Data Sources | Create, run, edit, and observe simulated sources |
| Recordings & Samples | Manage reusable real or prepared data |
| Scenarios | Build and run repeatable test flows |
| Evidence | Review and export captured run evidence |
| Settings | Project and environment settings |
| Admin | User and role management |
| Activity / Audit | Filter team and automation history |

## 6. Screen Inventory

| Screen / Flow | Design Status | Next Output |
|---|---|---|
| Login | Baseline spec ready | Mockup / implementation |
| Project Entry | Baseline spec ready | Mockup / implementation |
| Project Overview | Baseline spec ready | Mockup / implementation |
| Create Data Source Wizard | Baseline spec ready | Mockup / implementation |
| Scan Real Source Flow | Baseline spec ready | Mockup / implementation |
| Recording Flow | Baseline spec ready | Mockup / implementation |
| Replay Flow | Baseline spec ready | Mockup / implementation |
| Data Source Detail | Baseline spec ready | Mockup / implementation |
| Full Schema Editor | Baseline spec ready | Mockup / implementation |
| Recordings & Samples | Baseline spec ready | Mockup / implementation |
| Scenario Builder | Baseline spec ready | Mockup / implementation |
| Scenario Run View | Baseline spec ready | Mockup / implementation |
| Evidence List / Detail | Baseline spec ready | Mockup / implementation |
| Evidence Export Dialog | Baseline spec ready | Mockup / implementation |
| Admin UI | Baseline spec ready | Mockup / implementation |
| Activity / Audit View | Baseline spec ready | Mockup / implementation |
| Project Lifecycle Actions | Baseline spec ready | Mockup / implementation |
| Credentials Handling | Baseline spec ready | Mockup / implementation |
| Retention / Cleanup | Baseline spec ready | Mockup / implementation |

## 7. Design Milestones

| Milestone | Goal | Screens / Flows |
|---|---|---|
| 1. Operational Foundation | Make shared runtime state understandable | Project Overview, Data Sources List, runtime panel, activity indicators |
| 2. Primary Product Flow | Design the main Scan -> Record -> Replay path | Create wizard, scan, record, replay, evidence capture |
| 3. Editing And Reuse | Make schemas and reusable data manageable | Schema editor, recordings/samples, import/export, edit locks |
| 4. Scenarios And Faults | Support repeatable test flows | Scenario builder, scenario run view, fault configuration |
| 5. Evidence And Admin | Complete team-ready workflows | Evidence detail/export, Admin users, role-aware UI |
| 6. Operational Governance | Make shared usage safe over time | Activity/audit, automated runs, credentials, retention, cleanup |
| 7. Visual System | Make screens consistent and buildable | Status badges, tables, forms, dialogs, spacing, typography |

## 8. Definition Of Done For A Screen Spec

A screen is ready for mockup or implementation when these are defined:

| Required Item | Done |
|---|---|
| User goal | [ ] |
| Primary actions | [ ] |
| Secondary actions | [ ] |
| Main layout | [ ] |
| Data shown | [ ] |
| Empty state | [ ] |
| Loading state | [ ] |
| Error state | [ ] |
| Permission/role behavior | [ ] |
| Concurrent editing behavior, if relevant | [ ] |
| Runtime/stale-data behavior, if relevant | [ ] |
| Destructive confirmations, if relevant | [ ] |
| Accessibility notes | [ ] |

## 9. Cross-Screen UI Rules

| Rule | Applies To |
|---|---|
| Runtime state must be visible | Overview, Data Sources, Scenario Run, Evidence |
| Initiator/owner must be visible | Runs, scenarios, edits, evidence |
| Long-running actions show progress | Scan, record, import, export, replay |
| Errors explain recovery | All screens |
| Empty states offer next action | All list and setup screens |
| Destructive actions explain impact | Stop, delete, overwrite, import |
| Credentials are never exposed | Real source scan, summaries, evidence, exports |
| Automated runs are visible | Overview, runtime panel, evidence, activity |
| Artifact size/age is visible where useful | Recordings, samples, evidence |
| Large lists support search/filter/sort | Sources, schemas, recordings, events, evidence |
| Color is not the only status signal | All status indicators |
| Keyboard and focus behavior are designed | All interactive screens |

## 10. Recommended Next Step

| Step | Why |
|---|---|
| Start UI-001 to UI-005 from `UI_TASKS.md` | Foundation patterns reduce rework across all screens |
| Then UI-010 to UI-023 | Login, project entry, overview, data sources, runtime panel |
| Then UI-030 to UI-047 | Primary Scan -> Record -> Replay and deep data-source work |
| Include UI-013, UI-014, UI-024, UI-025, UI-039A, UI-074, UI-094, UI-095 before frontend freeze | These close project lifecycle, audit, automation, credentials, retention, responsive, and notification gaps |

## 11. Current Design Decisions

| Topic | Decision |
|---|---|
| UI model | Shared Web UI |
| Auth | Login/password required |
| Roles | Admin and User |
| Project access | All Users see all projects |
| Main flow | Scan real source -> Record -> Replay |
| Data-source creation | One protocol-extensible wizard |
| MVP protocols | OPC UA and Modbus TCP |
| Schema editing | Full editor |
| Evidence capture | Automatic for every run |
| Evidence export | Report, Full bundle, Value timeline CSV |
| Recordings/samples | Import/export independently |
| Concurrent editing | Automatic edit locks |
| Accessibility | WCAG 2.2 AA baseline |
| Project lifecycle | Create, rename, duplicate, archive, delete, import, export |
| Automated runs | Visible in runtime, activity, and evidence surfaces |
| Credentials | Masked and never exposed in summaries/evidence/exports |
| Retention | Size, age, last-used, dependency impact, cleanup entry points |

## 12. Handoff Readiness

| Area | Status | Notes |
|---|---|---|
| Product scope | Ready | Aligned with `SPEC.md` capabilities at MVP UI level |
| Information architecture | Ready | Main sections and navigation model defined |
| Screen inventory | Ready | All MVP screens have baseline specs |
| Implementation tasks | Ready | `UI_TASKS.md` provides IDs, priorities, dependencies, outputs, acceptance criteria |
| Edge cases | Ready | Failure, stale, locked, partial, import/export, credential, retention cases covered |
| Accessibility baseline | Ready | WCAG 2.2 AA baseline defined |
| Visual design | Ready for first pass | Needs concrete component styling during UI implementation |
| Protocol details | Ready for first pass | OPC UA/Modbus field baseline defined; exact field behavior can be refined per implementation |
| Prototype/mockups | Not required before task kickoff | Can be produced from `UI_SCREEN_SPECS.md` if team wants visual review first |

## 13. Non-Blocking Follow-Ups

These should not block the first UI development pass, but should be refined while implementing relevant tasks.

| Follow-Up | Related Tasks |
|---|---|
| Exact OPC UA and Modbus field labels and validation copy | UI-039, UI-039A |
| Exact error and empty-state text | UI-002, UI-091, UI-092 |
| Exact table density and column order | UI-003, UI-020, UI-021, UI-050, UI-070 |
| Exact responsive breakpoints | UI-094 |
| Exact evidence export file naming | UI-072 |
| Exact retention thresholds and cleanup policy | UI-074 |
| Optional contextual help/glossary behavior | UI-090, UI-091 |
