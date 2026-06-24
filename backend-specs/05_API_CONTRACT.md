# API Contract (DRAFT)

Status: **DRAFT — proposal for approval.** REST/OpenAPI surface plus live (SSE)
endpoints for the backend, derived from `SPEC.md` capabilities and the UI
surfaces in `UI_SCREEN_SPECS.md`. Conforms to `ARCHITECTURE.md` (REST + OpenAPI
for commands/queries/test-control; SSE for live; path-based major versioning;
API layer enforces authz) and decisions D6 (SSE-only) and D7 (`/api/v1`).

## Conventions

- Base path: `/api/v1`. Additive within the major version; breaking → `/api/v2`.
- OpenAPI via springdoc; the spec is generated and is the contract of record.
- Auth: shared mode requires `Authorization: Bearer <OIDC JWT>`; trusted local
  mode accepts requests as the implicit principal (`08_AUTH_AND_MODES.md`). The
  API layer enforces authorization (flexible permission model, D2).
- Optimistic concurrency (D4): mutating endpoints return `ETag` (entity
  `version`); clients send `If-Match`; mismatch → `409 Conflict`.
- Collections: cursor pagination (`?cursor=&limit=`), filtering, sorting; dense
  tables in the UI rely on server-side filter/sort.
- Errors: RFC 9457 `application/problem+json` with stable `type` codes; partial
  failure is first-class (the UI assumes it).
- Secrets: never returned in responses, summaries, evidence, or exports.

## Resource endpoints (commands & queries)

### Projects
- `GET /projects` · `POST /projects` · `GET /projects/{id}` ·
  `PATCH /projects/{id}` · `DELETE /projects/{id}`
- `POST /projects/{id}/duplicate` · `POST /projects/{id}/archive`
- `POST /projects/import` (multipart) · `GET /projects/{id}/export` (artifact)

### Data sources
- `GET /projects/{pid}/data-sources` · `POST …/data-sources` ·
  `GET …/data-sources/{id}` · `PATCH …/{id}` · `DELETE …/{id}` ·
  `POST …/{id}/duplicate`
- Runtime: `POST …/{id}/start` · `POST …/{id}/stop`
- Schema: `GET …/{id}/schema` · `PUT …/{id}/schema` (full editor save)

### Scan (create-from-real-source)
- `POST …/data-sources/scan/test-connection` → reachability/auth result
- `POST …/data-sources/scan` → starts a scan job; returns `jobId`
- `GET …/data-sources/scan/{jobId}` → progress / partial / discovered schema
  (states: unreachable, auth failure, partial, large schema, unknown type)

### Recordings & samples
- `GET/POST …/recordings` · `GET …/recordings/{id}` · `DELETE …/recordings/{id}`
- `POST …/data-sources/{id}/recording/start` · `…/recording/stop`
- `POST …/recordings/import` · `GET …/recordings/{id}/export`
- `GET/POST …/samples` · `GET …/samples/{id}` · `…/samples/{id}/export`

### Scenarios
- `GET/POST …/scenarios` · `GET/PATCH/DELETE …/scenarios/{id}` ·
  `POST …/scenarios/{id}/duplicate`
- `GET …/scenarios/{id}/validate`
- `POST …/scenarios/{id}/run` · `POST …/runs/{runId}/stop`

### Replay
- `POST …/data-sources/{id}/replay` (body: recording/sample ref + deterministic
  settings + compatibility ack) → starts a replay run

### Runs (and test-control for automation)
- `GET …/runs` · `GET …/runs/{id}` · `POST …/runs/{id}/stop`
- Automation-facing (SPEC "Control Simulations From Automated Tests"):
  `POST …/runs` (start a source/scenario), `GET …/runs/{id}/state` (readiness +
  runtime state for polling), `POST …/runs/{id}/stop`. Automated runs carry an
  automation initiator label (never anonymous).

### Evidence
- `GET …/evidence` · `GET …/evidence/{id}` · `POST …/evidence/{id}/export`
  (format + scope; secret exclusion explicit) · export-failure retry

### Observability (history; live is via SSE below)
- `GET …/runtime-events` (filter by source/run/type/time)
- `GET …/activity` (audit; filter by actor/action/object/time) — **distinct**
  from runtime-events
- `GET …/data-sources/{id}/clients` (current + history)

### Settings & admin
- `GET/PATCH /projects/{id}/settings` · `GET/PATCH /environment/settings`
- `GET /admin/users` · `PATCH /admin/users/{id}/roles` (shared, admin-only)

### Edit leases (shared concurrency)
- `POST …/{objectType}/{id}/edit-lease` · `DELETE …/{objectType}/{id}/edit-lease`
  (advisory; backs UI read-only/locked states)

### Health
- `GET /healthz` (liveness) · `GET /readyz` (readiness)

## Live updates (SSE)

One-way streams (D6). Each event carries a type + payload; clients reconnect with
`Last-Event-ID`.

- `GET /api/v1/projects/{pid}/stream/runtime` — runtime context: active runs,
  runtime events, health/stale state (backs the Runtime Context Panel).
- `GET /api/v1/data-sources/{id}/stream/values` — live (conflated/throttled)
  values for the Values tab.
- `GET /api/v1/data-sources/{id}/stream/clients` — client connect/disconnect.
- `GET /api/v1/runs/{id}/stream` — run/scenario progress, current step.

Live values are explicitly the conflated path, not the full-fidelity recording
path (`ARCHITECTURE.md`); the UI labels them as potentially stale.

## Role-aware behavior

The same endpoints exist for all principals; authorization decides allowed
actions. Per `SPEC.md` (Assign User Roles): `user` may observe everything
(incl. evidence) and **operate runtime** — start/stop data-sources and start/stop
replay and scenario runs; `admin` additionally edits projects/data-sources/
schemas/scenarios, imports/exports, manages retention, and manages access. The
UI prefers preventing invalid actions before submit; the API still enforces.

> Note: this `user` scope is broader than the current `UI_SCREEN_SPECS.md`
> wording (which limits `user` to starting stopped sources). The UI role docs
> should be synced to `SPEC.md`.

## Open questions for reviewer

- Confirm cursor (vs offset) pagination as the standard.
- Confirm scan/recording modeled as async jobs polled via `GET` (+ SSE for
  progress) vs. synchronous calls.
