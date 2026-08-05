## MODIFIED Requirements

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
