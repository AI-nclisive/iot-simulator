# Auth, Roles, Concurrency & Deployment Modes (DRAFT)

Status: **DRAFT — proposal for approval.** How the one build serves both
deployment modes, how authentication/authorization work, how shared edits stay
safe, and how secrets and the DB connection are configured. Conforms to
`ARCHITECTURE.md` (two modes from one build; OAuth2/OIDC in shared mode; API
enforces authz; optimistic concurrency; external secrets/DB config) and decisions
D2 (flexible permission model) and D4 (optimistic concurrency).

## Deployment modes (one build)

Selected by configuration (env var / Spring profile), not by a separate build:

- **Trusted local** (`MODE=local`, default): single user, **auth optional/off**.
  Requests run as an implicit principal `local` with full control. No login
  screen (`SPEC.md` "Use Product Without Login"; `frontend/docs/DESIGN.md` Local Entry).
- **Shared team** (`MODE=shared`): multi-user, **authenticated** via OAuth2/OIDC.
  Workspace content is blocked until authentication succeeds.

Both modes run on Linux, Windows, macOS (`SPEC.md`). The same API and UI
structure serve both; mode changes permissions and authorship visibility, not
layout (`frontend/docs/DESIGN.md`).

## Authentication (shared mode)

- The backend is an **OAuth2/OIDC resource server**: it validates bearer JWTs
  (issuer, audience, signature via JWKS). It does **not** own a password
  lifecycle (`frontend/docs/UI_SCREEN_SPECS.md` Admin UI).
- Providers: Keycloak, AWS Cognito, Azure Entra ID (`STACK.md`). Provider config
  (issuer URI, audience, JWKS) comes from environment / external config —
  `EnvironmentSettings` holds references, not secrets.
- Identity = JWT `sub` + claims; mapped to a `User` record on first sight.

## Authorization (flexible permission model — D2)

- Internally, authorization is checked against **permissions** (fine-grained
  capabilities, e.g. `source.start`, `source.configure`, `recording.export`,
  `admin.access`).
- **Roles map to permission sets.** Externally exposed roles **now**: `admin`,
  `user`. The model expands to viewer/operator/editor/admin later **without
  changing enforcement points** (the same permission checks stay).
- Baseline mapping (per `SPEC.md`, Assign User Roles):
  - `user`: observe everything (incl. evidence) and **operate runtime** —
    start/stop data-sources, start/stop replay and scenario runs. Cannot edit,
    import/export, or manage access.
  - `admin`: everything a `user` can do **plus** edit projects/data-sources/
    schemas/scenarios, import/export, retention/cleanup, and access management.
- Enforcement is in the **API layer** (`ARCHITECTURE.md`). Local mode grants the
  implicit principal the full permission set.
- Role↔claim mapping: a configurable claim (e.g. `roles`/`groups`) maps IdP
  groups to product roles; default mapping documented in `EnvironmentSettings`.

> Governance: `SPEC.md` states the baseline admin/user model
> (`00_DECISIONS_AND_INDEX.md` D2/G3); this spec implements the agreed flexible
> model. `SPEC.md`, `ARCHITECTURE.md`, and `frontend/docs/UI_SCREEN_SPECS.md` are
> aligned on the `user` start/stop scope.

## Optimistic concurrency & shared edit safety (D4)

- Every editable entity carries `version` (bigint). Mutations require the
  expected version (`If-Match`/ETag, `05_API_CONTRACT.md`); mismatch →
  `409 Conflict`. **No silent overwrite** (`ARCHITECTURE.md`).
- **Advisory edit lease** (`edit_leases` table, `04_…`): when a user opens an
  editor (e.g. Full Schema Editor, Scenario Builder), the backend grants a
  time-bounded lease. Others get a **read-only view** while the lease holds
  (`frontend/docs/DESIGN.md` Concurrent Editing; `frontend/docs/UI_TASKS.md` UI-005). Leases expire so a
  crashed/abandoned session self-recovers (stale-lock recovery).
- The lease is advisory UX protection; the `version` check is the authoritative
  guard against lost updates.

## Secrets & PKI

- Secrets, credentials, and PKI/keystore material come from **env vars / an
  external secret store**, never from repo files (`ARCHITECTURE.md`).
- Scan/record connection secrets are used in-memory; persistence is optional and,
  where supported, stored only via the secret store — **never** in entity rows,
  exports, evidence, activity, or summaries (`frontend/docs/UI_SCREEN_SPECS.md` Credential
  Handling).
- Exportable artifacts are built by a path that structurally excludes secrets
  (`06_ARTIFACT_FORMATS.md`).

## Database connection (both modes)

- The Postgres `DataSource` is **externally configured** via env (URL, user,
  password-from-secret). One build targets containerized Postgres + mounted
  volume **or** a managed instance (RDS/Cloud SQL) — no engine-specific features
  assumed (`STACK.md`).

## Object storage (D3)

- One `ObjectStore` port (`platform` module); adapters: **filesystem** (local
  default) and **S3-compatible** (shared). Large artifacts only; no blobs in
  Postgres (`ARCHITECTURE.md`).

## Open questions for reviewer

- Confirm the configurable claim used for role mapping (`roles` vs `groups`).
- Confirm edit-lease default TTL and whether the schema editor and scenario
  builder both require a lease at P1.
