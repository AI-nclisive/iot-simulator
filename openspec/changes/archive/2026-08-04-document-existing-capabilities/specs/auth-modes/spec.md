## Purpose

One build serves both a trusted-local single-user mode and an authenticated
shared-team mode, with the same API/UI structure in both, so deployment mode
changes permissions and authorship visibility, never the product's shape.

## ADDED Requirements

### Requirement: Deployment mode is configuration, not a separate build
The runtime mode SHALL be selected by configuration (env var / Spring
profile), not a separate build artifact. In trusted local mode (default),
authentication is optional/off and requests run as an implicit `local`
principal with full control - no login screen. In shared mode, the workspace
is blocked until OIDC authentication succeeds.

#### Scenario: Local mode has no login
- **WHEN** the app runs in trusted local mode
- **THEN** a request is served as the implicit `local` principal without any
  authentication step

### Requirement: Shared mode is an OIDC resource server
In shared mode, the backend SHALL validate bearer JWTs (issuer, audience,
signature via JWKS) and SHALL NOT own a password lifecycle. Provider config
(issuer URI, audience, JWKS) SHALL come from environment/external config, not
secrets embedded in the workspace settings. Identity is the JWT `sub` plus
claims, mapped to a `User` record on first sight.

#### Scenario: Invalid or missing bearer token is rejected
- **WHEN** a request in shared mode carries no bearer token, or one that
  fails JWKS signature validation
- **THEN** the request is rejected before reaching workspace content

### Requirement: Authorization is permission-based with role-to-permission mapping
Authorization SHALL be checked against fine-grained permissions (e.g.
`source.start`, `source.configure`, `recording.export`, `admin.access`), with
roles mapping to permission sets. Externally, only `admin` and `user` roles
are exposed today; `user` can observe everything (including evidence) and
operate runtime (start/stop data sources, capture, replay, and scenario
runs), but cannot edit, import/export, or manage access; `admin` can do
everything `user` can plus edit projects/data-sources/schemas/scenarios,
import/export, manage retention, and manage access. Enforcement SHALL live
in the API layer; local mode grants the implicit principal the full
permission set.

#### Scenario: A user role cannot edit a schema
- **WHEN** a principal with the `user` role attempts to edit a data source's
  schema
- **THEN** the API rejects the request as unauthorized

### Requirement: Optimistic concurrency plus advisory edit leases guard shared edits
Every editable entity SHALL carry a `version`; mutations require the expected
version and a mismatch returns `409 Conflict` - no silent overwrite. Opening
an editor (e.g. the full schema editor or scenario builder) SHALL acquire a
time-bounded advisory edit lease; other users see a read-only view while the
lease holds. Leases SHALL expire so a crashed/abandoned session self-recovers.
The `version` check, not the lease, is the authoritative guard against lost
updates.

#### Scenario: Stale write is rejected even if the lease was never taken
- **WHEN** a client sends an update with a `version` older than the entity's
  current version, without ever having held an edit lease
- **THEN** the update is rejected with `409 Conflict` regardless of lease
  state

### Requirement: A simulated endpoint's accepted credentials are not a real-source secret
A data source's own simulated-server accepted credentials (what an Edge
Device must present to connect) are part of the data source's definition and
SHALL be stored as a salted PBKDF2 hash in `security_config`, exported with
the project so a re-imported project reproduces the same auth. This is
distinct from scan/record connection secrets used to reach a *real* source,
which SHALL remain session-only and are never persisted or exported.

#### Scenario: Simulated-endpoint credentials survive export/import as hashes
- **WHEN** a data source with username/password security config is exported
  and re-imported
- **THEN** the re-imported source accepts the same credentials, stored only
  as a hash, never as plaintext

### Requirement: Secrets and PKI come from external configuration only
Secrets, credentials, and PKI/keystore material SHALL come from environment
variables or an external secret store, never from repo files. Exportable
artifacts SHALL be built by a path that structurally excludes real-source
secrets (see the `artifact-formats` capability); the simulated-endpoint
credential hashes above are the sole deliberate exception.

#### Scenario: No secret value in a config file
- **WHEN** the repository or a committed config file is inspected
- **THEN** it contains no live secret value, only references to where the
  runtime environment supplies one
