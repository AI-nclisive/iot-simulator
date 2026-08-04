## MODIFIED Requirements

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
