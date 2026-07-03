# API Inventory

Current backend API (`/api/v1` + infra endpoints, base package `com.ainclusive.iotsim.api`) with a proposed logical regrouping. Documentation only — no code has moved.

Groups follow the **user workflow** — *platform → workspace → sources → data → execution → monitoring* — cross-checked against the domain aggregates in `backend-specs/03_DOMAIN_MODEL.md`.

> **Boundary note:** almost every path is nested under `/projects/{pid}/...` because a project *owns* everything. That's data ownership, not grouping. The **Projects** group is only the endpoints acting on the workspace *itself* (create/rename/archive/export) — the other groups handle its *contents*. The `{pid}` just says which workspace an item belongs to.

**Fits group?** column: `✓` clean fit · `⚠` strained fit (reason given). The ⚠ rows are summarized in *Where the grouping strains* at the bottom.

## Table 1 — Endpoints

| Method | Path | What / why | → Proposed group | Fits group? |
|---|---|---|---|---|
| GET | `/api/v1/meta` | API name + version | Platform | ✓ |
| GET | `/actuator/health` | Liveness/readiness probe (Spring Actuator) | Platform | ✓ |
| GET | `/actuator/info` | Build/version info (Spring Actuator) | Platform | ✓ |
| GET | `/api/v1/projects` | List projects | Projects | ✓ |
| POST | `/api/v1/projects` | Create a project | Projects | ✓ |
| GET | `/api/v1/projects/{id}` | Get one project | Projects | ✓ |
| PUT | `/api/v1/projects/{id}` | Update project | Projects | ✓ |
| POST | `/api/v1/projects/{id}/duplicate` | Deep-copy project | Projects | ✓ |
| POST | `/api/v1/projects/{id}/archive` | Archive project | Projects | ✓ |
| DELETE | `/api/v1/projects/{id}` | Delete project | Projects | ✓ |
| GET | `/api/v1/projects/overview` | Dashboard counts across projects | Projects | ✓ Aggregation read over the workspace root |
| POST | `/api/v1/projects/{id}/export` | Export project as ZIP | Projects | ✓ Whole-workspace bundle |
| POST | `/api/v1/projects/import` | Import project from ZIP | Projects | ✓ Whole-workspace bundle |
| POST | `/api/v1/projects/{pid}/data-sources` | Create a data source | Data Sources | ✓ |
| POST | `/api/v1/projects/{pid}/data-sources/synthetic` | Create synthetic-backed source | Data Sources | ✓ Same `DataSource` entity, `basis=SYNTHETIC` |
| POST | `/api/v1/projects/{pid}/data-sources/scan/test-connection` | Probe reachability/auth | Data Sources | ✓ Pre-create discovery |
| POST | `/api/v1/projects/{pid}/data-sources/scan` | Start async scan | Data Sources | ✓ Feeds source creation |
| GET | `/api/v1/projects/{pid}/data-sources/scan/{jobId}` | Poll scan job | Data Sources | ✓ Feeds source creation |
| POST | `/api/v1/projects/{pid}/data-sources/scan/{jobId}/create` | Create source from scan | Data Sources | ✓ |
| GET | `/api/v1/projects/{pid}/data-sources/{did}/schema` | Get tag/address layout | Data Sources | ✓ Schema is a child of DataSource |
| PUT | `/api/v1/projects/{pid}/data-sources/{did}/schema` | Replace schema | Data Sources | ✓ Child of DataSource |
| GET | `/api/v1/projects/{pid}/data-sources` | List data sources | Data Sources | ✓ |
| GET | `/api/v1/projects/{pid}/data-sources/{id}` | Get one data source | Data Sources | ✓ |
| PUT | `/api/v1/projects/{pid}/data-sources/{id}` | Update source | Data Sources | ✓ |
| DELETE | `/api/v1/projects/{pid}/data-sources/{id}` | Delete source | Data Sources | ✓ |
| DELETE | `/api/v1/projects/{pid}/data-sources/{id}/credentials` | Clear credentials | Data Sources | ✓ |
| POST | `/api/v1/projects/{pid}/data-sources/{id}/duplicate` | Copy source | Data Sources | ✓ |
| POST | `/api/v1/projects/{pid}/data-sources/{id}/start` | Spawn runtime worker | Data Sources | ⚠ Seam — runtime *control* is here, but the same source's live *health/values* are in Monitoring (one resource split across two groups) |
| POST | `/api/v1/projects/{pid}/data-sources/{id}/stop` | Stop runtime worker | Data Sources | ⚠ Same seam as `start` |
| GET | `/api/v1/projects/{pid}/recordings` | List recordings | Test Data | ✓ |
| POST | `/api/v1/projects/{pid}/recordings` | Create empty recording shell | Test Data | ✓ |
| GET | `/api/v1/projects/{pid}/recordings/{id}` | Get one recording | Test Data | ✓ |
| POST | `/api/v1/projects/{pid}/data-sources/{did}/recording/start` | Begin live capture | Test Data | ⚠ Capture is an *action over time* (like Execution), not at-rest data; sits here because it produces a Recording |
| POST | `/api/v1/projects/{pid}/data-sources/{did}/recording/stop` | End capture / finalize | Test Data | ⚠ Same as capture `start` |
| POST | `/api/v1/projects/{pid}/recordings/{id}/export` | Build + stream recording ZIP | Test Data | ✓ |
| GET | `/api/v1/projects/{pid}/recordings/{id}/download` | Stream stored recording ZIP | Test Data | ✓ |
| POST | `/api/v1/projects/{pid}/recordings/import` | Import recording from ZIP | Test Data | ✓ |
| GET | `/api/v1/projects/{pid}/samples` | List samples | Test Data | ✓ Sample is a view of a Recording |
| POST | `/api/v1/projects/{pid}/samples` | Create sample | Test Data | ✓ |
| GET | `/api/v1/projects/{pid}/samples/{id}` | Get one sample | Test Data | ✓ |
| DELETE | `/api/v1/projects/{pid}/samples/{id}` | Delete sample | Test Data | ✓ |
| POST | `/api/v1/projects/{pid}/samples/{id}/export` | Build + stream sample ZIP | Test Data | ✓ |
| GET | `/api/v1/projects/{pid}/samples/{id}/download` | Stream stored sample ZIP | Test Data | ✓ |
| POST | `/api/v1/projects/{pid}/samples/import` | Import sample from ZIP | Test Data | ✓ |
| GET | `/api/v1/projects/{pid}/scenarios` | List scenarios | Execution | ⚠ Authoring is *at-rest content* (like Test Data); grouped in Execution for workflow cohesion, not because it executes |
| POST | `/api/v1/projects/{pid}/scenarios` | Create scenario | Execution | ⚠ Authoring, not execution (see above) |
| GET | `/api/v1/projects/{pid}/scenarios/{id}` | Get one scenario | Execution | ⚠ Authoring, not execution |
| PATCH | `/api/v1/projects/{pid}/scenarios/{id}` | Update scenario | Execution | ⚠ Authoring, not execution |
| DELETE | `/api/v1/projects/{pid}/scenarios/{id}` | Delete scenario | Execution | ⚠ Authoring, not execution |
| POST | `/api/v1/projects/{pid}/scenarios/{id}/duplicate` | Copy scenario | Execution | ⚠ Authoring, not execution |
| GET | `/api/v1/projects/{pid}/scenarios/{id}/validate` | Validate without running | Execution | ⚠ Authoring-side check, not a run |
| POST | `/api/v1/projects/{pid}/scenarios/{id}/run` | **Start a scenario run** | Execution | ✓ Produces a `Run` (kind=SCENARIO) |
| POST | `/api/v1/projects/{pid}/data-sources/{did}/replay` | **Start a replay run** — play a recording through a source | Execution | ✓ Run-start action; produces a `Run` (kind=REPLAY) |
| POST | `/api/v1/projects/{pid}/data-sources/{did}/run-synthetic` | **Start a synthetic run** — continuous generated feed | Execution | ✓ Run-start action; produces a `Run` (kind=SYNTHETIC) |
| GET | `/api/v1/projects/{pid}/runs` | List runs | Execution | ✓ |
| POST | `/api/v1/projects/{pid}/runs` | Start a run (replay/scenario/synthetic) | Execution | ✓ |
| GET | `/api/v1/projects/{pid}/runs/{id}` | Get one run | Execution | ✓ |
| GET | `/api/v1/projects/{pid}/runs/{id}/state` | Live run state | Execution | ⚠ A *live read* (like Monitoring) but tied to a specific run resource, so it stays with Execution |
| POST | `/api/v1/projects/{pid}/runs/{id}/stop` | Stop a run | Execution | ✓ |
| GET | `/api/v1/projects/{pid}/active-runs` | Currently active runs (dashboard) | Execution | ✓ A filtered list of runs (≈ `GET /runs?state=active`) |
| GET | `/api/v1/projects/{pid}/evidence` | List run evidence | Execution | ⚠ Evidence is *output data at rest* (like Test Data); grouped with the run that produced it |
| GET | `/api/v1/projects/{pid}/evidence/{id}` | Get evidence + manifest | Execution | ⚠ Output data at rest (see above) |
| POST | `/api/v1/projects/{pid}/evidence/{id}/export` | Trigger evidence export | Execution | ⚠ Output data at rest |
| GET | `/api/v1/projects/{pid}/evidence/{id}/download` | Stream evidence bundle | Execution | ⚠ Output data at rest |
| GET | `/api/v1/data-sources/{id}/health` | Runtime state + last error | Monitoring | ⚠ Seam — source-scoped read whose *command* side (start/stop) lives in Data Sources |
| GET | `/api/v1/data-sources/{id}/clients` | Connected-clients snapshot + history | Monitoring | ⚠ Same source seam |
| GET | `/api/v1/projects/{pid}/runtime-events` | Historical runtime event log | Monitoring | ⚠ Run/source-scoped log — could also read as Execution audit |
| GET | `/api/v1/data-sources/{id}/stream/values` (SSE) | Push values snapshot + deltas | Monitoring | ✓ Live push |
| GET | `/api/v1/data-sources/{id}/stream/clients` (SSE) | Push client connect/disconnect | Monitoring | ✓ Live push |
| GET | `/api/v1/projects/{pid}/stream/runtime` (SSE) | Push runtime/health transitions | Monitoring | ✓ Live push |

## Table 2 — Proposed groups (6)

| Group | What composes it | Why this group exists |
|---|---|---|
| Platform | `Meta`, `/actuator/health`, `/actuator/info` | System/ops endpoints with no domain entity — API nameplate + liveness/build probes. Small but a genuine category |
| Projects | `Project` CRUD, `ProjectOverview`, `ProjectIO` (import/export) | The workspace *container itself* — create/rename/archive/export the whole thing. Not its contents |
| Data Sources | `DataSource` (create paths + manage + start/stop), `Schema` (child), `Scan` (discovery) | Define and run simulated devices. One `DataSource` aggregate; `basis` is metadata, `Schema` is its versioned child, `start/stop` is transient runtime control |
| Test Data | `Recording` (+ capture, import/export), `Sample` (+ import/export) | The captured/curated datasets you replay. `Recording 1─* Sample`, and Sample reuses the recording's timeline + ZIP format — one data group |
| Execution | `Scenario` (author + validate); `Run` + its start actions + `active-runs`; `Evidence` | The whole testing loop: author a flow, run it (one `Run` entity behind every start path), watch the run list, keep the `Evidence` it produces. Workflow bucket spanning 3 aggregates on purpose |
| Monitoring | `Health`, `ClientObservation`, `RuntimeEventHistory`, 3 SSE streams | Read-only "what's happening now" — cross-cutting live/historical state of sources and runs. Watching, not doing |

## Where the grouping strains

With **Platform** added and `active-runs` moved to Execution, **no endpoint is a true misfit** — every one has a defensible home. The remaining ⚠ rows aren't homeless; they sit on a **second axis (doing vs. watching, authoring vs. at-rest data)** that cuts across the by-object grouping:

1. **One resource, two groups (Data Sources ↔ Monitoring).** A source's *commands* (`start`/`stop`) are in Data Sources, but its *live reads* (`health`, `clients`, value stream) are in Monitoring — split by doing-vs-watching.
2. **Capture is a verb in a noun group.** `recording/start|stop` are actions over time (execution-like) living in **Test Data** because their *product* is a dataset.
3. **Scenario authoring isn't execution.** The scenario CRUD/validate endpoints are at-rest authored content — arguably **Test Data**-like — parked in Execution for workflow cohesion (author → run → evidence in one place).
4. **Evidence is output data, not an activity.** The evidence read/export endpoints are at-rest result data, grouped with the run that made them.
5. **`run/{id}/state` is a live read** kept with Execution because it's a sub-resource of a specific run, not general observation.

**Bottom line:** the group system *works* — 6 groups, every endpoint has an unambiguous home, zero true misfits. The ⚠ rows are inherent seams: the grouping is by **domain object**, so anything that's fundamentally a *read/observation* or *authored content* straddles two objects. The strict alternative (split every group into command vs. query, CQRS-style) removes the seams but doubles the group count for little navigational gain — not worth it.
