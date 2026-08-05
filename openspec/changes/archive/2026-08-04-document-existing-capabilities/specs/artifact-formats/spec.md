## Purpose

Artifact formats (evidence, project import/export, recording/sample export,
scenarios, faults, synthetic generation) are the serialization contracts that
keep exported data portable, safe, and forward-compatible.

## ADDED Requirements

### Requirement: Every artifact is versioned and excludes secrets
Every exportable/importable artifact SHALL carry a `formatVersion` (semver)
in its manifest. A reader SHALL accept an equal-or-older minor version within
its supported major version and SHALL reject an unknown newer version with a
clear message rather than guessing. Secrets, connection credentials, and PKI
material SHALL never be serialized into any artifact - enforced by the
export builder itself, not left to convention.

#### Scenario: Newer major version is rejected, not guessed at
- **WHEN** an artifact's `formatVersion` major component is newer than the
  reader supports
- **THEN** import fails with an explicit "unsupported newer version" error

### Requirement: Project export/import is a portable ZIP container
A project export SHALL be a ZIP with a `manifest.json` (`formatVersion`,
`exportedAt`, `productVersion`, project metadata, content index, per-entry
checksums) plus the project's data sources (with schemas and protocol
bindings), scenarios, non-secret settings, and selected recordings/samples.
Connection secrets, environment-level identity config, runtime state, live
data, and audit history SHALL be excluded.

#### Scenario: Exported project never carries a real-source secret
- **WHEN** a project containing a data source with a saved real-device
  connection secret is exported
- **THEN** the export ZIP contains that data source's schema and bindings but
  no connection secret field

### Requirement: Recording and sample export is lossless and replay-ready
A recording/sample export SHALL be a ZIP with a manifest (schema version and
referenced nodes, time range, value count, tags, checksums) plus value data
serialized directly from the value-timeline model, ordered by
(`source_time`, `seq`), so re-import is lossless and immediately replay-ready.
A sample export additionally records its `selection` (node subset + time
window).

#### Scenario: Round-trip preserves value order
- **WHEN** a recording is exported and then re-imported
- **THEN** replaying the re-imported recording reproduces the same value
  sequence and ordering as the original

### Requirement: Evidence artifact always shows origin and completeness
An evidence export SHALL be a ZIP with a manifest recording `runId`, `kind`,
`initiator`, start/end time, and a `completeness` of
`COMPLETE | PARTIAL | FAILED`, plus sections for value timelines, client
connection history, scenario metadata, runtime events, faults activated, and
errors. Origin (which run, who initiated it, how complete it is) SHALL always
be visible in the exported artifact, not just in the API response that
triggered the export.

#### Scenario: Partial evidence is labeled, not silently treated as complete
- **WHEN** a run's evidence export runs while some data is still missing
- **THEN** the manifest's `completeness` is `PARTIAL`, not `COMPLETE`

### Requirement: Scenario steps are typed per step type
A serialized `Scenario` SHALL have `deterministicSettings` and an ordered
list of steps, each with a `type` and params typed for that type: `START`/
`STOP` (target source), `REPLAY` (target source, recording/sample reference,
replay options, deterministic settings), `SYNTHETIC` (target source,
synthetic config), `FAULT` (inline fault definition, per the `domain-model`
capability - never a reference to a separate `Fault` record), `WAIT`
(duration or condition), `MARKER` (a label for evidence/timeline annotation).

#### Scenario: A FAULT step carries its definition inline
- **WHEN** a scenario is exported that includes a `FAULT` step
- **THEN** the step's `kind`/`layer`/`target`/`params` appear inline on that
  step in the export, with no separate fault-id lookup required

### Requirement: Fault params are typed per kind
A serialized fault definition SHALL have a `kind`
(`BAD_VALUE | MISSING_VALUE | DELAY | CONNECTION_DROP | TIMEOUT |
PROTOCOL_ERROR | SOURCE_UNAVAILABLE`), a `layer` (`NEUTRAL | PROTOCOL`), a
target (source and/or node), and kind-specific params (e.g. `DELAY.ms`,
`BAD_VALUE.qualityReason`, `CONNECTION_DROP.afterMs`). A fault's `intent` is
always intentional, so it is never auto-healed.

#### Scenario: DELAY fault carries its millisecond param
- **WHEN** a `DELAY` fault is serialized
- **THEN** its params include `ms`, and no other fault kind's params (e.g.
  `qualityReason`) are present

### Requirement: Synthetic generation is deterministic and pattern-typed
A serialized synthetic target SHALL specify a `pattern`
(`CONSTANT | RAMP | SINE | SQUARE | RANDOM_WALK | RANDOM_UNIFORM |
ENUM_CYCLE | STEP_SEQUENCE`), a range/bounds, and an update rate. A required
`seed` drives all randomness deterministically. Structural/identifier data
types (`GUID`, `STATUS_CODE`, `QUALIFIED_NAME`, `NODE_ID`,
`EXPANDED_NODE_ID`, `XML_ELEMENT`, `BYTES`, `DATETIME`) SHALL accept only the
`CONSTANT` pattern, carrying their value as `stringValue` or
`bytesValueBase64` instead of the numeric `value` field, since a dynamic
pattern has no physical meaning for a structural/identifier value.

#### Scenario: Structural type rejects a non-constant pattern
- **WHEN** a synthetic target of type `GUID` is configured with pattern
  `RANDOM_WALK`
- **THEN** the configuration is rejected; only `CONSTANT` is accepted for
  that data type

### Requirement: Deterministic run settings are recorded, not just applied
Replay, synthetic, and scenario runs SHALL serialize their `deterministic`
flag, `seed`, and ordering/timing mode into the run/evidence record, so the
settings that produced a given run's data remain inspectable afterward. The
serialized format SHALL NOT imply a client-delivery-timing guarantee the
system does not make.

#### Scenario: Seed is inspectable after the run ends
- **WHEN** a deterministic synthetic run completes
- **THEN** its evidence/run record shows the `seed` that was used, not just
  that determinism was enabled
