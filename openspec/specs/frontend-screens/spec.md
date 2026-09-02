# frontend-screens Specification

## Purpose
The screen inventory is the contract for which pages and guided flows exist,
what each is responsible for, and how they relate - so a shipped surface is
never invisible to this spec the way `Manual Schemas` was invisible to the
old `UI_SCREEN_SPECS.md`.
## Requirements
### Requirement: Page inventory
The product SHALL provide these project-scoped pages: `Project Overview`,
`Data Sources List`, `Data Source Detail`, `Manual Schemas` (list + editor),
`Recordings` (recording list + detail, with sample import as a dialog rather
than its own page), `Scenarios`, `Scenario Run View`, `Evidence List`,
`Evidence Detail`, `Activity View`, `Settings`, `Admin UI`; plus
account-entry pages `Login` (shared mode) and `Project Entry`. The
retention/cleanup admin surface is specified only as a shared-surface
contract; it has no page of its own yet.

#### Scenario: Manual Schemas is a first-class page, not a hidden feature
- **WHEN** a user wants to author a reusable node structure independent of
  any data source
- **THEN** `Manual Schemas` list and editor pages exist and are reachable
  from primary navigation (see the `frontend-shell` capability)

#### Scenario: Samples are managed from the Recordings surface
- **WHEN** a user imports a sample
- **THEN** they do it from a dialog on the Recordings surface, not from a
  separate top-level Samples page

### Requirement: Guided flows for creation and editing
The product SHALL provide these guided flows: `Create Data Source Wizard`,
`Scan Real Source`, `Full Schema Editor`, `Recording Flow`, `Replay Flow`,
`Scenario Builder`.

#### Scenario: Full Schema Editor is reachable outside the creation wizard
- **WHEN** a user wants to edit an already-saved data source's schema
- **THEN** the Full Schema Editor opens as a standalone editing surface, not
  only as a step inside the creation wizard

### Requirement: Cross-cutting shared surfaces
The product SHALL provide these shared surfaces, usable from multiple pages
rather than duplicated per page: `Deterministic Run Settings`,
`Runtime Dashboard`, `Automated Run Visibility`, `Credential Handling`,
`Retention & Cleanup`, `Notifications`.

#### Scenario: Deterministic Run Settings is reused across run types
- **WHEN** a user configures a replay run and, separately, a scenario run
- **THEN** both use the same `Deterministic Run Settings` surface rather than
  two separately built forms

### Requirement: Data Source Detail exposes one consistent tab set
The `Data Source Detail` page SHALL expose the same tab set for every data
source regardless of protocol or basis: Overview, Schema, Values, Clients,
Events, and Settings.

#### Scenario: Modbus and OPC UA sources share the same tab set
- **WHEN** viewing a Modbus TCP data source and, separately, an OPC UA data
  source
- **THEN** both detail pages show the same six tabs, differing only in tab
  content

### Requirement: Edit-lock state reflects a real backend lease
When a user opens an editor that acquires an edit lease (e.g. the schema
editor), the UI SHALL reflect the real lease state from the backend - showing
a `locked by <other user>` banner when another user holds the lease - not an
always-unlocked placeholder.

#### Scenario: Second editor sees a locked banner
- **WHEN** user A opens the schema editor for a data source and user B opens
  the same editor while A's lease is active
- **THEN** B sees a read-only, locked-by-A banner instead of an editable form

### Requirement: Activity View supports filtering and pagination
`Activity View` SHALL let a user filter by actor, action, and object, and
SHALL support cursor-based pagination through history - not present as an
empty placeholder.

#### Scenario: Filtering by actor narrows the visible history
- **WHEN** a user filters `Activity View` by a specific actor
- **THEN** only that actor's events are shown, and paging further stays
  scoped to that filter

### Requirement: Runtime Dashboard keeps active work visible
The `Runtime Dashboard` shared surface SHALL show, for each active run:
active process (recording/replay/scenario), source scale (parameter count),
evidence state, initiator/authorship, recency, and a quick link back to the
affected object or evidence - staying compact rather than expanding to full
history.

#### Scenario: Quick link jumps to the affected object
- **WHEN** a user clicks an active run's quick link on the Runtime Dashboard
- **THEN** they land on that run's source or evidence detail page directly

### Requirement: Credential handling never surfaces secrets
Any surface that collects or displays real-source connection credentials
SHALL treat them as write-only: never echoed back in read views, exports,
evidence, or activity records.

#### Scenario: Saved connection form never re-displays the password
- **WHEN** a user reopens the connection settings for a data source that
  already has a saved password
- **THEN** the password field is empty/masked, never pre-filled with the
  actual saved value

### Requirement: Modbus real-source scans target a selected unit
The Create Data Source Wizard SHALL show a `Unit ID` field only when the user
selects both `Modbus TCP` and `Real source`. The field SHALL default to `1`,
accept whole-number values from `0` through `255`, and send the selected value
with the real-source connection test, scan, and creation from a completed scan.
The wizard SHALL omit the field and its request property for protocols that do
not use a Modbus unit ID.

#### Scenario: A Modbus real-source scan uses a non-default unit
- **WHEN** a user selects Modbus TCP, selects Real source, and enters unit ID
  `7`
- **THEN** the wizard sends `unitId: 7` when testing, scanning, and creating
  the source from that scan

#### Scenario: OPC UA scan setup has no unit ID
- **WHEN** a user selects OPC UA and Real source
- **THEN** the wizard does not show a Unit ID field or send a `unitId` property

### Requirement: Wizard date/time picker controls remain available above action panels
The Create Data Source Wizard SHALL render the Schedule step's date/time picker
popup above every wizard action panel that overlaps it, so every date and time
control remains visible and operable.

#### Scenario: Schedule picker overlaps the wizard footer
- **WHEN** a user opens a Schedule step date/time picker whose popup extends
  into the wizard footer
- **THEN** the popup renders above the footer and its date and time controls
  remain available to the user

