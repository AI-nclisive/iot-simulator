# Frontend ↔ Backend Contract Map (ready core)

Maps the **already-implemented** backend REST surface (`/api/v1`) to the
**mock data shapes the frontend consumes today**, field by field, so the UI can
be wired to the real API instead of fixtures.

Scope = the "ready core" the backend already delivers: **projects, data sources,
schema, scan, recordings, replay** (`IS-022…032`, `IS-035…045`). Live
observability, evidence, scenarios, faults and auth are **not** covered here —
they need backend tasks first (see the gap index at the end).

Status when written: frontend has **no API client** — every screen reads Zustand
stores seeded from `frontend/src/**/mock-*.ts`. The backend exposes the contracts
below. This document is the join.

> Ground truth: backend DTOs read from `api/.../**Controller.java` + DTO records;
> FE shapes read from `frontend/src/**`. Enum values read from
> `domain/.../{Protocol,RuntimeState}.java` and
> `protocol-model/.../{DataType,NodeKind,Access,ValueRank}.java`.

---

## 0. Cross-cutting integration rules

These apply to every call and are the bulk of the wiring work.

1. **Base URL / proxy.** All endpoints live under `/api/v1`. Add
   `VITE_API_BASE_URL` and a dev proxy in `vite.config.ts` (currently none).
2. **Generate the client from OpenAPI.** The backend already serves OpenAPI +
   Swagger (`IS-030`, `/openapi.json`, `/swagger-ui`). Generate the TS client and
   types from it instead of hand-writing — it makes the OpenAPI doc the single
   source of truth and kills the enum/field drift listed below.
3. **Optimistic concurrency (ETag/If-Match).** `GET`/`POST`/`PUT` on **projects,
   data-sources, schema** return an `ETag: "<version>"` header and the body
   carries `version`. Mutations (`PUT`) **require** `If-Match: "<version>"`; a
   stale value returns **409**. The FE stores currently drop `version` — it must
   be threaded through every read so edits/saves can send `If-Match`. This also
   backs the UI edit-lock/stale patterns (`UI-005`).
4. **Errors are `application/problem+json`** (RFC 9457, `IS-032`): `status`,
   `title`, `detail`, `type`. Map to the shared error/toast patterns
   (`UI-002`, `UI-095`). Notable codes: `409` concurrency, `428` precondition
   required (missing `If-Match`), `404`, `400`.
5. **Auth header.** Local mode (default) needs no header. Shared mode expects
   `Authorization: Bearer <jwt>` — attach the token when present. (Shared-mode
   enforcement itself is still a backend todo, `IS-075…077`.)
6. **Enum value alignment.** Backend serializes Java enum **names**
   (`OPC_UA`, `RUNNING`, `FLOAT64`, `READ_WRITE`). The FE uses display unions
   (`"OPC UA"`, `"Active"`, `"float"`). Either map at the client edge or align
   the OpenAPI schema. The per-area tables below give the exact mappings.
7. **Secrets are write-only.** `connectionConfig.secret`/`secretRef` are sent on
   create/scan, **never returned** in any response (`IS-042`). The FE masking
   behavior (`UI-039A`) already matches this — never read secrets back.

### Path corrections (FE assumptions → real backend)

The FE invented endpoint names that don't exist. Real paths:

| FE assumed | Real backend path |
| --- | --- |
| `/projects/{id}/sources` | `/api/v1/projects/{projectId}/data-sources` |
| `/projects/{id}/artifacts` | `/api/v1/projects/{projectId}/recordings` |
| `/projects/{id}/runs`, `/evidence`, `/scenarios`, `/auth/*`, `/admin/*` | **do not exist yet** — backend todo |

---

## 1. Projects

**Backend:** `ProjectController` — base `/api/v1/projects`

| Method | Path | Request | Response | Headers |
| --- | --- | --- | --- | --- |
| GET | `/projects` | — | `ProjectResponse[]` | — |
| POST | `/projects` | `CreateProjectRequest{name, description}` | `ProjectResponse` (201) | sets `ETag`, `Location` |
| GET | `/projects/{id}` | — | `ProjectResponse` | sets `ETag` |
| PUT | `/projects/{id}` | `UpdateProjectRequest{name, description}` | `ProjectResponse` | needs `If-Match`, sets `ETag` |
| DELETE | `/projects/{id}` | — | — (204) | — |

`ProjectResponse` = `{id, name, description, status, createdAt, updatedAt, createdBy, version}`

**FE consumer:** `ProjectSummary` (`frontend/src/shell/mock-workspace.ts`)

| FE field | Source | Note |
| --- | --- | --- |
| `id` | `ProjectResponse.id` | ✅ direct |
| `name` | `ProjectResponse.name` | ✅ direct |
| `configuredSources` | — | ⚠️ derive = `GET data-sources`.length, **or** add aggregation (`IS-054`) |
| `runningSources` | — | ⚠️ derive = count `runtimeState=RUNNING`, **or** `IS-054` |
| `reusableArtifacts` | — | ⚠️ derive = `GET recordings`.length, **or** `IS-054` |
| `lastActivity` | — | ❌ needs runtime-event/activity history (`IS-055`/`IS-083`) |

Backend extras unused by the list row: `description`, `status`, `createdAt`,
`updatedAt`, `createdBy`, `version` (`version` → ETag flow).

**Verdict:** CRUD wires now. The three count badges need either 2 extra fetches
per project or the overview aggregation endpoint (`IS-054`); `lastActivity` needs
a backend task.

---

## 2. Data sources

**Backend:** `DataSourceController` — base `/api/v1/projects/{projectId}/data-sources`

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| GET | `` | — | `DataSourceResponse[]` |
| POST | `` | `CreateDataSourceRequest` | `DataSourceResponse` (201, ETag, Location) |
| GET | `/{id}` | — | `DataSourceResponse` (ETag) |
| PUT | `/{id}` | `UpdateDataSourceRequest` | `DataSourceResponse` (If-Match, ETag) |
| DELETE | `/{id}` | — | 204 |
| DELETE | `/{id}/credentials` | — | `DataSourceResponse` |
| POST | `/{id}/start` | — | `DataSourceResponse` |
| POST | `/{id}/stop` | — | `DataSourceResponse` |

`DataSourceResponse` = `{id, projectId, name, protocol, basis, schemaId,
schemaVersion, endpoint, runtimeConfig, enabled, runtimeState, credentialState,
createdAt, updatedAt, createdBy, version}`
`CreateDataSourceRequest` = `{name, protocol, basis, endpoint, runtimeConfig,
connectionConfig{mode, username, secret, secretRef}}`
`UpdateDataSourceRequest` = `{name, endpoint, runtimeConfig, enabled, connectionConfig{…}}`

**FE consumer:** `DataSourceRow` (`frontend/src/surfaces/mock-data-sources.ts`)

| FE field | Source | Note |
| --- | --- | --- |
| `id` | `id` | ✅ |
| `name` | `name` | ✅ |
| `protocol` | `protocol` | 🔁 map `OPC_UA→"OPC UA"`, `MODBUS_TCP→"Modbus TCP"` |
| `endpoint` | `endpoint` | ✅ |
| `status` (`Active`/`Stopped`) | `runtimeState` | 🔁 `RUNNING→Active`; `STOPPED/STARTING→Stopped` |
| `health` (`Healthy`/`Warning`/`Error`) | `runtimeState` (partial) | 🔁 `RUNNING→Healthy`, `STALE→Warning`, `ERROR→Error`; full health = `IS-053` |
| `parameterCount` | — | ⚠️ derive from schema node count (`GET schema`, count `VARIABLE`) |
| `process` (`Recording`/`Replay`) | — | ❌ live run state — `IS-051`/runs |
| `clients` | — | ❌ connected-client observation — `IS-052` |
| `lastOperator` | `createdBy` (approx) | ⚠️ only *creator* exists; no "last operator"/`updatedBy` |
| `assignedReplayArtifactId` | — | ❌ no persisted replay assignment — `IS-069` |

Backend enum — `RuntimeState`: `STOPPED, STARTING, RUNNING, ERROR, STALE`.
Backend extras: `projectId, basis, schemaId, schemaVersion, runtimeConfig,
enabled, credentialState, timestamps, version`.

**Verdict:** CRUD + start/stop + credential clear wire now (with enum mapping).
The *live* columns (`process`, `clients`, full `health`) and
`assignedReplayArtifactId` need backend tasks; `parameterCount` is a derived
fetch.

---

## 3. Schema / parameters

**Backend:** `SchemaController` — base `…/data-sources/{dataSourceId}/schema`

| Method | Request | Response |
| --- | --- | --- |
| GET | — | `SchemaResponse` (ETag) |
| PUT | `SaveSchemaRequest{nodes[]}` | `SchemaResponse` (ETag) |

`SchemaResponse` = `{id, dataSourceId, version, nodes: NodeDto[]}`
`NodeDto` = `{nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description}`

**FE consumer:** `SchemaParameter` (`frontend/src/surfaces/mock-schema-parameters.ts`)

The FE shows a flat **parameter** list = `NodeDto` where `kind=VARIABLE`
(filter out `FOLDER`; build the tree from `parentId`).

| FE field | Source | Note |
| --- | --- | --- |
| `id` | `nodeId` | ✅ (rename) |
| `name` | `name` | ✅ |
| `path` | `path` | ✅ |
| `type` (`float`/`int`/`bool`/`string`) | `dataType` | 🔁 `FLOAT32/FLOAT64→float`; `INT16/UINT16/INT32/UINT32/INT64/UINT64→int`; `BOOL→bool`; `STRING→string`; `BYTES→` (no FE type — decide) |
| `unit` | `unit` | ✅ |
| `description` | `description` | ✅ |
| `min` / `max` | — | ❌ no range metadata on the node (synthetic-range concern, `IS-062`) |
| `hasDependent` | — | ❌ no dependency info — `IS-094` |

Backend enums: `NodeKind{FOLDER,VARIABLE}`, `Access{READ,READ_WRITE}`,
`ValueRank{SCALAR,ARRAY}`, `DataType{BOOL,INT16,UINT16,INT32,UINT32,INT64,UINT64,
FLOAT32,FLOAT64,STRING,BYTES}`. FE drops `kind`, `parentId`, `valueRank`,
`access` — keep them when round-tripping a `PUT` so the save doesn't lose data.

**Verdict:** read + full-editor save wire now. `min`/`max`/`hasDependent` are FE
extras with no backend home yet.

---

## 4. Scan (create-from-real-source)

**Backend:** `ScanController` — base `…/data-sources/scan`

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| POST | `/test-connection` | `ScanRequest` | `ConnectionTestResponse{status, message}` |
| POST | `` | `ScanRequest` | `StartScanResponse{jobId, status}` (202, Location) |
| GET | `/{jobId}` | — | `ScanJobResponse` |
| POST | `/{jobId}/create` | `CreateFromScanRequest` | `DataSourceResponse` (201) |

`ScanRequest` = `{protocol, endpointUrl, maxNodes, connectionConfig{mode, username, secret, secretRef}}`
`ScanJobResponse` = `{jobId, status, truncated, discoveredCount, unknownCount, message, nodes: DiscoveredNodeResponse[]}`
`DiscoveredNodeResponse` = `NodeDto fields + unknownType:boolean`
`CreateFromScanRequest` = `{name, endpoint, typeResolutions[]{nodeId, dataType, valueRank, access, exclude}}`

**FE consumer:** `WizardFormState` (`create-data-source-wizard-page.tsx`)

| FE field | Source | Note |
| --- | --- | --- |
| `scanEndpoint` | `ScanRequest.endpointUrl` | ✅ |
| `scanCredentialMode` | `connectionConfig.mode` | ✅ values match (`anonymous`/`password`/`external-ref`) |
| `scanUsername`/`scanPassword`/`scanSecretRef` | `connectionConfig.{username,secret,secretRef}` | ✅ |
| `scanTestResult` (`idle`/`success`/`auth-error`/`error`) | `ConnectionTestResponse.status` | 🔁 confirm BE status values; `auth-error` may need message/code parsing |
| `scanState` (`idle`/`scanning`/`complete`/`partial`/`large`/`error`/`unknown`) | `ScanJobResponse.status` + `truncated` + `unknownCount` | 🔁 `truncated→large/partial`, `unknownCount>0→unknown` |
| unknown-type handling (`UI-033`) | `DiscoveredNodeResponse.unknownType` → `typeResolutions[]` | ✅ good fit |

**Integration note:** scan is **async/poll** (202 + `GET /{jobId}`). The FE mock
is synchronous — it must implement polling on `jobId` until `status` is terminal.

**Verdict:** strongest match in the whole core — wires now, just add polling and
status-value mapping.

---

## 5. Recordings

**Backend:** `RecordingController` (`…/recordings`) + `RecordingCaptureController`
(`…/data-sources/{dataSourceId}/recording/{start,stop}`)

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| GET | `…/recordings` | — | `RecordingResponse[]` |
| POST | `…/recordings` | `CreateRecordingRequest{dataSourceId}` | `RecordingResponse` (201) |
| GET | `…/recordings/{id}` | — | `RecordingResponse` |
| POST | `…/{dataSourceId}/recording/start` | — | `RecordingResponse` (201) |
| POST | `…/{dataSourceId}/recording/stop` | — | `RecordingResponse` |

`RecordingResponse` = `{id, projectId, dataSourceId, schemaVersion, origin, valueCount, createdAt, createdBy, version}`

**FE consumers:** `ReusableArtifact` (`mock-artifacts.ts`) and the richer
`RecordingRow` (`mock-recordings.ts`).

| FE field | Source | Note |
| --- | --- | --- |
| `id` | `id` | ✅ |
| `sourceId` | `dataSourceId` | ✅ (rename) |
| `valueCount` | `valueCount` | ✅ |
| `createdAt`/`capturedAt` | `createdAt` | ✅ (FE formats) |
| `createdBy`/`capturedBy` | `createdBy` | ✅ |
| `origin` | `origin` | 🔁 confirm BE value set vs FE `captured`/`imported`/`synthetic` |
| `name` | — | ❌ **recordings are unnamed on the backend** |
| `type` (`Recording`/`Sample`) | — | ❌ no Sample concept — `IS-068` |
| `protocol`, `sourceName` | — | ⚠️ derive via `dataSourceId → data-source` |
| `duration`/`durationLabel` | — | ❌ no duration field (only `valueCount`) |
| `status` (`Ready`/`Partial`) | — | ❌ no capture-completeness flag (`UI-034` partial-save) |
| `parameterCount` | — | ⚠️ derive from `schemaVersion → schema` |
| `tags`, `lastUsedAt`, `sizeKb` | — | ❌ no metadata — `IS-068`/`IS-070` |

**Verdict:** capture start/stop + list/get wire now, but the Recordings &
Samples surfaces (`UI-050…053`) are only **partially** backable: no name, no
samples, no duration/size/tags/status/protocol. These map to `IS-068` (samples)
and `IS-070` (import/export). Treat recordings as a thin list until then.

---

## 6. Replay

**Backend:** `ReplayController` — `POST …/data-sources/{dataSourceId}/replay`
`ReplayRequest{recordingId}` → `ReplayResponse{recordingId, dataSourceId, valueCount}`

**FE consumer:** replay flow (`replay-flow-page.tsx`,
`ReplayUiState = idle|running|completed|failed`) + `assignReplayArtifact`.

| FE concept | Source | Note |
| --- | --- | --- |
| start replay | `POST replay {recordingId}` | ✅ (FE `artifactId` → `recordingId`) |
| value count on completion | `ReplayResponse.valueCount` | ✅ |
| progress states (`running`→`completed`/`failed`) | — | ❌ no progress stream — `IS-051` SSE / runs |
| `deterministicSettings` (seed/preset/ordering) | — | ❌ `ReplayRequest` has no deterministic fields — `IS-063` |
| persisted `assignReplayArtifact` | — | ❌ not persisted on the source — `IS-069` |

**Verdict:** fire-and-return replay wires now; live progress, deterministic
controls (`UI-055`) and assignment persistence (`UI-054`) need backend tasks.

---

## 7. Meta

`GET /api/v1/meta` → `{name:"iot-simulator", apiVersion:"v1"}`. Use for an API
reachability/version probe at app boot.

---

## Gap index → backend tasks

What the FE needs that the core does **not** provide yet, mapped to existing
register tasks (so the UI knows what to stub vs. wait for):

| Gap (FE expectation) | Backend task |
| --- | --- |
| Project overview counts + `lastActivity` | `IS-054`, `IS-055` |
| Live values (Values tab) | `IS-046` (SSE) + `IS-051` |
| Connected clients (`clients`, Clients tab) | `IS-046` + `IS-052` |
| Full source health | `IS-053` |
| Runtime events history (Events tab) | `IS-055` |
| Active runs / `process` / run progress | `IS-046` + `IS-051`, runs `IS-089` |
| Evidence list/detail/export | `IS-050`, `IS-056`, `IS-057`, `IS-058` |
| Recording **name**, samples, tags, duration, status | `IS-068`, `IS-070` |
| Replay deterministic settings | `IS-063` |
| Persisted replay assignment | `IS-069` |
| Schema `min`/`max` (ranges) | `IS-062` |
| Schema dependency (`hasDependent`) | `IS-094` |
| Login / roles / permissions | `IS-075`, `IS-076`, `IS-077` |
| Edit leases (stale-lock) | `IS-079`, `IS-080`, `IS-081` |
| Project import/export | `IS-073` |
| List pagination/filtering | `IS-074` |
| Admin user-management endpoints | *(new)* `IS-118` |

## Bottom line

- **Wire today (with enum mapping + ETag plumbing + scan polling):** Project
  Entry/CRUD, Data Sources list & detail (config/start/stop/credentials), full
  Schema editor, the entire Scan→create flow, recording capture start/stop +
  basic list, fire-and-return Replay.
- **Needs a backend task first:** everything *live* (values/clients/events/health/
  runs), evidence, named recordings & samples, deterministic replay, auth/admin,
  import/export, pagination.
