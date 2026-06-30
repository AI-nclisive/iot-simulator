# IS-085 — Scenario model + steps (design)

**Task:** IS-085 [BE] · Wave F — Advanced workflows & hardening · P2
**Issue:** [#106](https://github.com/AI-nclisive/iot-simulator/issues/106)
**Owning spec:** `backend-specs/03_DOMAIN_MODEL.md` (Scenario / ScenarioStep) + `06_ARTIFACT_FORMATS.md` (serialized scenario model) + `05_API_CONTRACT.md` (Scenarios endpoints); supports `SPEC.md` → *Build Custom Scenarios*
**Branch:** `feat/IS-085-scenario-model`

## Problem

The `scenarios` and `scenario_steps` tables already exist (Flyway `V2`), and the
domain model is specified (`03 §Scenario/ScenarioStep`, `06 §Scenario model`),
but there is **no repository, domain service, or REST controller** — nothing
creates, stores, lists, or edits a scenario. The `runs` table even carries a
`scenario_id` FK that nothing populates.

IS-085 delivers the scenario **model + steps + CRUD** so a scenario (an ordered
list of typed steps) can be authored, persisted, listed, edited, duplicated, and
deleted. It is the prerequisite for IS-086 (validation + run execution), which
in turn unblocks IS-089 scenario-kind runs.

## Scope boundary (agreed)

- **In IS-085:** domain model, persistence, and the full CRUD controller —
  `GET/POST /scenarios`, `GET/PATCH/DELETE /scenarios/{id}`,
  `POST /scenarios/{id}/duplicate`. `status` is always `DRAFT`.
- **Step `params` are opaque JSON passthrough** (stored/round-tripped as-is, like
  `sample.selection`). Only model-level checks here; deep per-type typing is
  IS-086.
- **Out of scope (→ IS-086):** `GET /scenarios/{id}/validate`,
  `POST /scenarios/{id}/run`, `READY`/`INVALID` status transitions, cross-entity
  reference checks (targetSourceId existence/compatibility, recording/fault refs),
  step execution.
- **Out of scope (other tasks):** user-activity audit (IS-083), runtime events,
  UI (UI-062/064).

## Architecture

Mirrors the existing pure-CRUD artifact `sample` (`domain/sample`,
`persistence/sample`, `api/sample`). No new Flyway migration — the tables exist
in `V2`; jOOQ classes are already generated.

```
domain/.../scenario/
  Scenario.java          record(id, projectId, name, status, deterministicSettings(JSON String),
                                steps(List<ScenarioStep>), createdAt, updatedAt, createdBy, version)
  ScenarioStep.java      record(ordinal, type(String), targetSourceId?(String), params(JSON String))
  ScenarioService.java   CRUD + duplicate; model-level validation; Row→domain mapping
persistence/.../scenario/
  ScenarioRepository.java      interface
  JooqScenarioRepository.java  one transaction per write
  ScenarioRow.java             scenarios columns + List<ScenarioStepRow> steps
  ScenarioStepRow.java         ordinal, type, targetSourceId, params
api/.../scenario/
  ScenarioController.java      /api/v1/projects/{projectId}/scenarios
  (Create/Update request records, ScenarioResponse, StepDto)
```

## Domain model

- `Scenario.status` is always `DRAFT` in IS-085 (READY/INVALID transitions belong
  to IS-086 validation).
- `deterministicSettings` and `step.params` are **opaque JSON strings**
  (passthrough); default `{}`.
- `steps` are ordered; the server **normalizes `ordinal` to request order**
  (`0..n-1`), eliminating gaps/duplicates regardless of what the client sends.

## Persistence

`ScenarioRow` is an aggregate: the scenario columns plus its
`List<ScenarioStepRow> steps`, populated together to avoid N+1 (mirrors how
`RunRow` carries its source ids).

- `create(projectId, name, detSettings, steps, createdBy) → ScenarioRow` —
  insert scenario + all `scenario_steps` in **one transaction**.
- `findById(id) → Optional<ScenarioRow>` (with steps).
- `findByProject(projectId) → List<ScenarioRow>` and
  `findByProjectPaged(afterAt, afterId, limit)` — cursor pagination
  (`created_at DESC, id DESC`), consistent with IS-074.
- `update(id, name, detSettings, steps, expectedVersion) → ScenarioRow` —
  **replace-all** steps (delete + reinsert ordered), bump `version`, optimistic
  check against `expectedVersion`.
- `deleteById(id)` — FK `on delete cascade` removes steps.
- Duplicate is composed in the service (read → create copy), not a repo method.

**Replace-all rationale:** the model is an ordered `steps[]`; replacing the whole
list on update is simpler and atomic, and avoids a granular step-mutation API
surface that nothing needs until the UI builder lands.

## API (contract `05`)

Base: `/api/v1/projects/{projectId}/scenarios`. ETag = `"version"`; optimistic
concurrency via `If-Match` on `PATCH` (mirrors `ProjectController` /
`DataSourceController`).

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/scenarios` | paged (`cursor`, `limit`) → `ScenarioResponse` incl. steps |
| `POST` | `/scenarios` | create; `name` required → 201 + `ETag` |
| `GET` | `/scenarios/{id}` | → 200 + `ETag` |
| `PATCH` | `/scenarios/{id}` | partial update; **`If-Match` required** → 200 + `ETag` |
| `DELETE` | `/scenarios/{id}` | → 204 |
| `POST` | `/scenarios/{id}/duplicate` | copy (status DRAFT, name `"<name> (copy)"`, steps copied) → 201 + `ETag` |

**PATCH field semantics:** a field absent (`null`) in the request body is left
unchanged; a present field is applied. When `steps` is present it **replaces the
whole list** (there is no per-step patch). `name` present-but-blank is a 400.

## Model-level validation (IS-085 only)

- `type` ∈ {`START`,`STOP`,`REPLAY`,`SYNTHETIC`,`FAULT`,`WAIT`,`MARKER`} → else 400.
- `params` must be a valid JSON object (else 400); empty → `{}`.
- `START` / `STOP` require `targetSourceId`.
- Scenario is always scoped/filtered by `projectId` (a scenario from another
  project is a 404, like `sample`).

Explicitly **not** validated here: `targetSourceId` existence/compatibility,
recording/fault references, READY/INVALID — all IS-086.

## Error handling

Reuses the existing `error` package: `ResourceNotFoundException`
(Project/Scenario → 404), `IllegalArgumentException` (bad input → 400),
`PreconditionRequiredException` (missing `If-Match` → 428), optimistic-version
mismatch → 409.

## Testing (TDD)

- **Repository IT** (Testcontainers Postgres): create / find (with steps) /
  update (replace-all) / duplicate / delete; step ordering; cascade delete.
- **Service unit**: validation rules, not-found, optimistic version, duplicate
  copying (steps + DRAFT status + name suffix).
- **Controller web-layer**: every endpoint, status codes, ETag/If-Match
  semantics, pagination — in the style of existing `*ControllerTest`.

## Definition of done

`./gradlew build` green; IS-085 checkbox flipped in `backend-specs/TASKS.md` in
the same PR (CI catalog-sync gate); board → In review via `/open-pr`.
