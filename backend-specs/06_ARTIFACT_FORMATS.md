# Artifact Formats (DRAFT)

Status: **DRAFT — proposal for approval.** Serialization formats for evidence,
project import/export, recording/sample export, scenarios, faults, and synthetic
generation. Conforms to `ARCHITECTURE.md`: exportable/importable artifacts are
**versioned**, **exclude secrets and private keys**, and **newer-than-supported
versions fail safely**. All operate on the protocol-neutral model
(`01_PROTOCOL_NEUTRAL_MODEL.md`).

## Versioning rules (all artifacts)

- Every artifact has a `formatVersion` (semver) in its manifest.
- Reader accepts equal-or-older minor within the supported major; an **unknown
  newer** version is rejected with a clear message (UI has the
  "unsupported newer version" state).
- Secrets, credentials, connection passwords, and PKI material are **never**
  serialized — enforced at the export builder, not just by convention.

## Project import/export

A portable container for a whole simulator setup (SPEC "Import And Export
Simulator Projects").

- Container: ZIP with a top-level `manifest.json`.
- `manifest.json`: `formatVersion`, `exportedAt`, `productVersion`, project
  metadata, content index, and a per-entry checksum.
- Contents: project, data-sources (incl. schemas + protocol bindings), scenarios,
  faults, settings (non-secret), and selected recordings/samples (data files).
- Excluded: connection secrets, environment-level identity config, runtime state,
  live data, audit history (audit stays server-side).
- Recordings/samples included as the recording/sample export format below.

## Recording / sample export

- Container: ZIP with `manifest.json` + value-timeline data files.
- Manifest: `formatVersion`, source schema (version + nodes referenced),
  `timeRange`, `valueCount`, `tags`, checksums.
- Value data: compact columnar/encoded files keyed by `node_id`, ordered by
  `source_time, seq` — a direct serialization of the value-timeline model so
  re-import is lossless and replay-ready.
- Sample export additionally records the `selection` (node subset + time window).

## Evidence artifact

Portable proof of a run (SPEC "Export Run Evidence", P0).

- Container: ZIP with `manifest.json` + sections.
- Manifest: `formatVersion`, `runId`, `kind`, `initiator`, `startedAt`/`endedAt`,
  `completeness` (`COMPLETE | PARTIAL | FAILED`), source/scenario context.
- Sections: value timelines, client connection history, scenario metadata,
  runtime events, faults activated, errors. Origin (which run, who initiated,
  completeness) is always visible (`DESIGN.md` Evidence Path).
- Export formats: ZIP bundle (default); a JSON summary subset for quick sharing.
  Secret exclusion is explicit in the export dialog (`UI_SCREEN_SPECS.md`).

## Scenario model (serialized)

Used in storage (`scenario_steps`), project export, and the builder API.

- Scenario: `name`, `deterministicSettings`, ordered `steps[]`.
- Step (`type` + typed `params`):
  - `START` / `STOP`: `targetSourceId`.
  - `REPLAY`: `targetSourceId`, recording/sample ref, replay options,
    deterministic settings.
  - `SYNTHETIC`: `targetSourceId`, synthetic config (below).
  - `FAULT`: fault ref or inline fault definition (below).
  - `WAIT`: duration or condition.
  - `MARKER`: label (for evidence/timeline annotation).
- Validation: a scenario is `READY` only when steps reference existing/compatible
  targets and required params are present (`UI_TASKS.md` UI-064).

## Fault model (serialized)

(SPEC "Simulate Faults And Unreliable Conditions".)

- `kind`: `BAD_VALUE | MISSING_VALUE | DELAY | CONNECTION_DROP | TIMEOUT |
  PROTOCOL_ERROR | SOURCE_UNAVAILABLE`.
- `layer`: `NEUTRAL` (applied to neutral values) or `PROTOCOL` (mapped to a
  protocol-specific fault by the worker — `01_…` §5, `02_…`).
- `target`: source and/or node selection.
- `params`: per-kind (e.g. `DELAY.ms`, `BAD_VALUE.qualityReason`,
  `CONNECTION_DROP.afterMs`).
- `intent`: always intentional → never auto-healed (`ARCHITECTURE.md`).

## Synthetic generation model (serialized)

(SPEC "Generate Synthetic Data".)

- Per target node (or node group): `pattern` +
  `range`/`bounds` + `updateRateMs`.
- Patterns: `CONSTANT | RAMP | SINE | SQUARE | RANDOM_WALK | RANDOM_UNIFORM |
  ENUM_CYCLE | STEP_SEQUENCE`.
- Determinism: `seed` (required when deterministic) drives all randomness;
  generation uses the explicit clock so the same seed + clock ⇒ identical series
  (`01_…` §4). Boundary-case presets supported (min/max/edge values).

## Deterministic run settings (serialized)

Shared by replay, synthetic, and scenarios (`UI_SCREEN_SPECS.md` Deterministic
Run Settings).

- `deterministic` (bool), `seed`, `ordering`/`timing` mode, and a recorded scope
  note. Persisted into run/evidence so settings are inspectable after the fact.
- The format must not imply client delivery-timing guarantees the system does not
  make (`ARCHITECTURE.md`).

## Open questions for reviewer

- Confirm ZIP+manifest as the container for all bundle artifacts.
- Confirm the synthetic pattern set for v1.
