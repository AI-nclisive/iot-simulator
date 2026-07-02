# OPC UA Endpoint Security & Authentication — Roadmap Design

Date: 2026-07-02
Status: **DRAFT — for review.**
Scope: backend (`IS-131`, `IS-132`, `IS-133`). Frontend configuration UI is a
separate `UI-XXX` task, out of scope here.

## Goal

Make a simulated OPC UA data source behave like a real OPC UA server with
respect to security: it can run **without authentication** (today's behaviour) or
**with authentication**, and — at the transport layer — with or without message
signing/encryption. The user configures this per data source; the configuration
is part of the simulation definition and travels with the project (save/reuse,
import/export).

A real OPC UA server exposes a set of endpoints, each a combination of
(SecurityPolicy × MessageSecurityMode), and each advertising the user-token
policies it accepts (Anonymous, UserName, Certificate). We simulate that surface
faithfully but pragmatically.

## Non-goals

- No product-login changes. The web-UI/product auth (OIDC, roles — `08`,
  Wave E IS-075..082) is unrelated; this is about the *simulated OPC UA server's*
  own auth surface.
- No issued-token / Kerberos user tokens.
- No frontend work in these tasks (separate `UI-XXX`).

## Delivery in three phases

Implemented in order; each is its own `IS-XXX` task, branch, and PR (repo rule:
one task per PR). This document is the shared spec for all three.

| Phase | Task | Delivers |
|-------|------|----------|
| 1 | **IS-131** | Username/Password (and Anonymous) user-token authentication. No PKI. |
| 2 | **IS-132** | Transport message security: server certificate/PKI, `Sign`/`SignAndEncrypt` endpoints, client-cert trust. |
| 3 | **IS-133** | X.509 certificate user tokens (builds on Phase 2 PKI/trust). |

## Configuration model (shared)

A single object describes what the simulated server **advertises** and
**validates**. Stored in a **new first-class `security_config` jsonb column** on
`data_sources` (not inside `runtime_config`) — mirroring the V9 precedent that
promoted `simulatorPort` out of the `runtime_config` bag. This keeps it separate
from the synthetic-generation config and explicit in the domain/API/export.

```jsonc
{
  "userTokens": {
    "anonymous": true,                 // accept anonymous sessions
    "username": {
      "enabled": false,
      "users": [
        { "username": "operator", "passwordHash": "<salted-hash>" }
      ]
    },
    "certificate": { "enabled": false } // Phase 3
  },
  "messageSecurity": {                  // Phase 2
    "policies": ["NONE"],               // NONE | BASIC256SHA256 | AES128_SHA256_RSAOAEP | AES256_SHA256_RSAPSS
    "modes":    ["NONE"]                // NONE | SIGN | SIGN_AND_ENCRYPT
  }
}
```

Rules / semantics:

- **Backward compatibility:** absent/empty `security_config` ≡ today's server
  (SecurityPolicy `None`, `MessageSecurityMode` `None`, Anonymous only). Existing
  rows and existing worker tests keep passing unchanged.
- At least one user-token type must be accepted (validation error otherwise).
- If `username.enabled` is true, at least one user is required.
- The worker materialises the **endpoint matrix** = `policies × modes`, and each
  endpoint advertises the enabled user-token policies. `NONE` policy is only
  valid with `NONE` mode (OPC UA rule); the worker enforces/normalises this.

### Password handling

The **accepted** credential defines the simulation, so it is persisted and
exported — but as a **salted hash** (`passwordHash`), never plaintext:

- On create/update the API accepts plaintext `password`; the domain hashes it
  (Spring Security `PasswordEncoder`, e.g. bcrypt) before persistence.
- The client presents plaintext at session-activation time; the worker hashes/
  verifies against the stored hash. Fully reproducible on import; no plaintext at
  rest or in exports.
- This is distinct from **real-source connection secrets** (scan/capture via
  `ConnectionConfigMsg` / `CredentialStore`), which remain session-only and are
  never persisted or exported (`08`). See Governance below.

> Note: with SecurityPolicy `None`, OPC UA transmits the UserName token password
> without transport encryption; Milo hands the validator the decoded password
> regardless of policy, so server-side hashing works in all phases.

## Backend pipeline

The per-source config must reach the worker's `Configure`. Today
`RuntimeStartSpecs.of(schemas, row)` carries only protocol/schema/port/
deterministic-settings; `bindAddress`/`advertisedHost` come from the supervisor's
deployment config, not the row. The security config is per-source, so it flows
from the row:

```
security_config (DB, jsonb, passwords hashed)
  → DataSourceRow.securityConfig (persistence, raw JSON string)
  → DataSource.securityConfig (domain, exposed via API as a non-secret view)
  → EndpointSecurity model (platform, neutral; parsed from JSON in domain)
  → RuntimeStartSpec.endpointSecurity (new field; platform)
  → Supervisor maps EndpointSecurity → proto SecurityConfig
  → WorkerClient.configure(..., SecurityConfig) → ConfigureRequest.security_config
  → OpcUaProtocolService.configure(...) → OpcUaServerRuntime
```

**Transport = a new proto `SecurityConfig` message on `ConfigureRequest`, not the
`options` string-map.** IS-128 used `options` for two scalar strings; the security
config is structured (a list of users, sets of policies/modes), which does not map
cleanly to `map<string,string>` and would force a JSON parser into the deliberately
lean, no-Spring worker (a new dependency — against `STACK.md`). A proto message is
type-safe, needs no parser, and a proto3 field-add is backward-compatible: the
worker-contract minor version bumps `1.2.0 → 1.3.0` (major unchanged, same as the
additive Capture RPC), old workers ignore the field, and a worker that receives no
`SecurityConfig` (empty message) falls back to None/Anonymous.

Two model representations, each in the module that owns its concern:
- **`EndpointSecurity`** — neutral record(s) in `platform` (beside `RuntimeStartSpec`).
  Carries no behaviour. Domain parses the stored JSON into it; the supervisor maps
  it to the proto. The worker never sees it (it gets the proto).
- **`PasswordHash`** — pure-JDK PBKDF2 utility in `protocol-model` (shared by both
  `domain` and `worker-opcua` via `worker-contract`'s `api` dependency). `encode`
  used by domain on write; `matches` used by the worker on session activation. No
  new dependency.

## Phase 1 — Username/Password (IS-131)

**Worker (`OpcUaServerRuntime`, Milo):**
- Replace the hard-coded single `USER_TOKEN_POLICY_ANONYMOUS` with the set derived
  from `userTokens` (`USER_TOKEN_POLICY_ANONYMOUS` and/or
  `USER_TOKEN_POLICY_USERNAME`).
- Set `OpcUaServerConfig.setIdentityValidator(...)` to a `UsernameIdentityValidator`
  that (a) allows anonymous only if `anonymous:true`, (b) verifies username +
  hashed password against the configured users. Reject → `Bad_UserAccessDenied` /
  `Bad_IdentityTokenRejected`.
- Transport stays `SecurityPolicy.None` / `MessageSecurityMode.None` in this phase.

**Domain/API:**
- `DataSourceService.create/update` accept a `securityConfig` (plaintext passwords
  in; hashed before persist). Validate the model (non-empty username; ≥1 user when
  username enabled; at least one accepted token type; JSON well-formed).
- Map the new column through `DataSourceRow` ↔ `DataSource`; expose in the API
  DTOs (passwords never returned — only presence/usernames).
- Export/import (`ProjectZipExporter` / `ProjectImportService`): include
  `security_config` (with hashes) so a project round-trips.

**Symmetry:** the client side (scan / `TestConnection` / capture) already carries
username/password via `ConnectionConfigMsg`, so create-from-scan against a
password-protected simulated source works without changes.

**Migration:** a new Flyway migration (`..._datasource_security_config.sql`, with a
collision-safe version chosen via the repo's `flyway-migration` rules) adds
`security_config jsonb not null default '{}'::jsonb`.

## Phase 2 — Transport message security (IS-132)

- Server certificate: auto-generate a self-signed cert + private key into a
  KeyStore on the worker's disk at first start; load via `DefaultCertificateManager`
  (the runtime already wires `DefaultTrustListManager` /
  `DefaultServerCertificateValidator`).
- Materialise secure endpoints for the configured `policies`/`modes`
  (`Basic256Sha256`, `Aes*`; `Sign`, `SignAndEncrypt`). Keep a `None` endpoint only
  if configured.
- Client-certificate trust: configurable trust behaviour; default for a simulator
  is trust-all (auto-accept) so tests/clients connect frictionlessly, with an
  option to require explicit trust later.
- Activate the `messageSecurity` part of the model.

## Phase 3 — X.509 user tokens (IS-133)

- Add `USER_TOKEN_POLICY_X509`; use Milo `X509IdentityValidator` composed with the
  username validator (`CompositeValidator`).
- Accept client user-certificates per the trust infrastructure from Phase 2.
- Activate `certificate.enabled`.

## Governance changes (require explicit approval before edit)

- **`08_AUTH_AND_MODES.md`** — clarify: the "secrets never persisted/exported"
  rule governs **real-source connection secrets**; the **expected credentials of a
  simulated server** are part of the data-source definition (stored as a salted
  hash, exported with the project).
- **`SPEC.md`** — clarify capability "Simulate OPC UA Data Sources": faithful
  simulation includes optional authentication and transport security. Exact
  wording to be proposed and confirmed before editing.
- **`02_WORKER_CONTRACT_AND_IPC.md`** — document the new `SecurityConfig` message
  on `ConfigureRequest` and the `1.2.0 → 1.3.0` contract bump.

## Testing

- **Unit:** model parse/validate; endpoint-matrix construction; password hashing/
  verification; None-mode normalisation rule.
- **Worker IT** (Milo client against the runtime, in the style of
  `OpcUaServerRuntimeIT`):
  - Phase 1: anonymous rejected when username-only; valid login succeeds; wrong
    password rejected; anonymous allowed when configured.
  - Phase 2: secure channel established with `Sign`/`SignAndEncrypt`.
  - Phase 3: certificate login succeeds/refused per trust.
- **Env caveats (memory):** ITs skip silently under `./gradlew build` unless
  `DOCKER_HOST` + `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` are exported (Colima);
  Milo swallows bind failures (detect via `getBoundEndpoints().isEmpty()`), and
  its behaviour must be re-verified on any Milo bump. Run the full module `check`
  (not `--tests X`) so Spotless/Checkstyle catch unused-import violations.

## Backward compatibility

- Additive proto field only → worker-contract `1.2.0 → 1.3.0` (major unchanged),
  the `Hello` handshake still matches, old workers ignore the field.
- Empty `security_config` / empty `SecurityConfig` reproduces exact current
  behaviour (None + Anonymous) → all existing worker and supervisor tests stay green.
- New DB column has a `'{}'` default → existing rows and import of older exports
  work unchanged.
