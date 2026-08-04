# frontend-shell Specification

## Purpose
The frontend shell is the stable, always-present structure (top bar, project
rail, navigation, shared interaction rules) that every project surface lives
inside, so users never have to relearn layout when moving between tasks or
between local and shared mode.
## Requirements
### Requirement: Stable shell layout
The UI SHALL present a minimal top bar (product identity, lightweight utility
entry points), a collapsible left project rail (project identity, quick
project switching, primary navigation), and a central work area for the
current task. The top bar SHALL NOT carry launch parameters, data-source
setup choices, large evidence blocks, or detailed runtime history.

#### Scenario: Top bar stays light across pages
- **WHEN** a user navigates from `Overview` to a data-source setup wizard
- **THEN** the top bar's content is unchanged; setup choices render in the
  central work area, not the top bar

### Requirement: Primary navigation groups by user intent
Primary navigation SHALL group the product as: Overview, Data Sources,
Recordings, Manual Schemas, Scenarios, Evidence, Settings, Admin. Labels SHALL
use product language only, never backend or architecture terms. The activity
history surface is currently reachable by URL (`/activity`) only and is NOT in
primary navigation; every other project-scoped surface SHALL have a nav entry,
so a new surface is never left URL-only.

#### Scenario: Every primary route has a nav entry
- **WHEN** a project-scoped route is reachable in the app (including
  `Manual Schemas`)
- **THEN** it has a corresponding entry in the primary navigation, with
  `/activity` as the one known exception — any other URL-only surface is a gap

#### Scenario: Activity is reachable only by URL today
- **WHEN** a user looks for activity history in primary navigation
- **THEN** they do not find it, and must navigate to `/activity` directly —
  a known limitation, not the intended end state

### Requirement: Overview is the operational command surface
`Overview` SHALL be the project's landing point and SHALL surface, at a
minimum: what changed recently, who started or changed it, where attention
is needed, and where to go next - concentrated as a compact runtime
dashboard, summarizing large parameter arrays rather than rendering them raw.
Deeper runtime history, alerts, and investigation belong on dedicated
surfaces, not on every page.

#### Scenario: A very large parameter array is summarized, not dumped
- **WHEN** an active run involves a data source with thousands of parameters
- **THEN** `Overview` shows a summary (e.g. count, a small pinned subset),
  not every parameter rendered raw

### Requirement: Primary UX path is scan, record, replay
The product SHALL visually and structurally favor
`Scan real source -> Record -> Replay` as the primary path, while keeping
manual schema creation, prepared-file import, and synthetic generation as
first-class alternative setup paths inside the same source-creation model,
not as separate mini-products.

#### Scenario: Alternative paths live inside the same creation model
- **WHEN** a user chooses "create from manual schema" instead of scanning
- **THEN** it is offered as a basis choice inside the same source-creation
  wizard, not as a separate standalone tool

### Requirement: One unified source-creation wizard
Creating a data source SHALL use one extensible wizard (choose protocol,
choose basis, enter connection/import/setup details, inspect and refine
schema, configure runtime behavior, review and create) rather than separate
tools per protocol or per origin type.

#### Scenario: Same wizard shape for every protocol
- **WHEN** a user creates a Modbus TCP source and, separately, an OPC UA
  source
- **THEN** both go through the same wizard step sequence, differing only in
  the protocol-specific fields inside the relevant steps

### Requirement: Configuration, runtime, and history stay visually distinct
The UI SHALL make it unambiguous whether the user is changing saved setup,
observing a running source, or inspecting historical output. Live values
(operational, potentially stale) SHALL be visually distinguished from
persisted artifacts (recordings, samples, evidence) and never mixed in one
undifferentiated panel.

#### Scenario: Live values panel is visually distinct from a recording's values
- **WHEN** a user views a running source's live Values tab and a saved
  Recording's values in the same session
- **THEN** the two are presented in visually distinct panels/labels, never
  merged into one table

### Requirement: Role-aware actions, not job-title-aware
The interface SHALL react to system role/permission, not job title.
Available actions SHALL change based on permission, and the UI SHALL prefer
preventing an invalid action before submit over relying on a failure message
after the fact.

#### Scenario: Unauthorized action is hidden or disabled, not just rejected server-side
- **WHEN** a `user`-role principal views a surface with an `admin`-only action
- **THEN** that action is not presented as available (disabled or hidden),
  not merely allowed to fail on submit

### Requirement: Authorship is visible in context
Shared activity SHALL surface who initiated a run, recording, evidence
export, edit session, or disruptive/destructive action, shown in context
next to the relevant object - not only on a separate audit page.

#### Scenario: Run card shows its initiator inline
- **WHEN** a user views an active run's card on `Overview`
- **THEN** the initiator is shown on that card, without navigating to the
  Activity page

### Requirement: Concurrent editing shows explicit state, not silent overwrite
The UI SHALL make an object's edit state explicit: editable, read-only
because of role, read-only because another user is editing (with an edit-lock
indicator), or stale because the underlying object changed. On a conflict,
the UI SHALL offer a concrete next step (reload, review changes later,
return to read-only) rather than a generic failure message.

#### Scenario: Lease-holder banner names who holds the lock
- **WHEN** user B opens an editor already locked by user A's edit lease
- **THEN** B sees a banner identifying A as the lock holder and a read-only
  view, not a generic "locked" message with no next step

### Requirement: Destructive actions require explicit confirmation
A destructive or disruptive action SHALL require confirmation that states
what object is affected, whether shared work may be interrupted, whether
connected devices/users may notice, and whether the action is reversible.

#### Scenario: Deleting a running data source is confirmed with impact stated
- **WHEN** a user attempts to delete a data source that currently has
  connected clients
- **THEN** the confirmation dialog states that connected clients will be
  disconnected before the delete proceeds

### Requirement: Failure states are designed, not incidental
Every important surface SHALL account for loading, empty, validation-error,
permission-restricted, reconnecting/stale-live, partial-failure, and
full-failure states, each pointing the user toward a recovery step rather
than just reporting that something went wrong.

#### Scenario: Stale live connection offers reconnect, not a dead panel
- **WHEN** a live values stream drops mid-session
- **THEN** the panel shows a reconnecting/stale state with a recovery action,
  not a frozen table with no indication anything is wrong

### Requirement: Responsive baseline without a mobile redesign
The product SHALL be desktop-first (`lg`+ is the fully supported two-column
layout). Tablet (`md`) SHALL collapse to one column with the project rail
behind a hamburger toggle; tables stay readable via horizontal scroll. Phone
widths SHALL remain accessible (hamburger toggle, scrolling tables/forms) but
are not a dedicated phone layout. Standard Tailwind breakpoints
(`sm`/`md`/`lg`/`xl`) apply consistently across the shell.

#### Scenario: Tablet width collapses the rail behind a toggle
- **WHEN** the viewport is 768px wide (`md`)
- **THEN** the project rail is hidden behind a hamburger toggle instead of
  the desktop two-column layout

