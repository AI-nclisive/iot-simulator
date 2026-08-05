## MODIFIED Requirements

### Requirement: Evidence artifact always shows origin and completeness
An evidence export SHALL be a ZIP with a manifest recording `runId`, `kind`,
`initiator`, start/end time, and a `completeness` of
`COMPLETE | STOPPED | PARTIAL | FAILED`, plus sections for value timelines,
client connection history, scenario metadata, runtime events, faults activated,
and errors. Origin (which run, who initiated it, how complete it is) SHALL
always be visible in the exported artifact, not just in the API response that
triggered the export. `STOPPED` SHALL indicate that a user deliberately ended
the run; `PARTIAL` SHALL indicate an incomplete artifact; and `FAILED` SHALL
indicate a failed run.

#### Scenario: Partial evidence is labeled, not silently treated as complete
- **WHEN** a run's evidence export runs while some data is still missing
- **THEN** the manifest's `completeness` is `PARTIAL`, not `COMPLETE`

#### Scenario: Intentional stop is distinct from incomplete evidence
- **WHEN** a user deliberately stops a running capture
- **THEN** the evidence manifest's `completeness` is `STOPPED` and an
  available exported artifact has status `READY`
