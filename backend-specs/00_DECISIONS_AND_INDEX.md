# Backend Specs — Decisions & Index (DRAFT)

Status: **DRAFT — proposals for approval.** Nothing here modifies `SPEC.md`,
`ARCHITECTURE.md`, or `STACK.md`. These documents fill the implementation-level
gaps those files deliberately leave open, so the backend codebase can be
generated. Where a proposal would touch a binding constraint, it is flagged and
needs explicit approval before code is written.

## Why this folder exists

The governance docs define **what** the product does (`SPEC.md`), the **binding
architecture** (`ARCHITECTURE.md`), and the **approved stack** (`STACK.md`).
They intentionally hold no domain model, API contract, DB schema, or worker
contract. Those are required before any backend code is correct. This folder
proposes them.

## Document index

| # | Document | Owns | Status |
| --- | --- | --- | --- |
| 00 | `00_DECISIONS_AND_INDEX.md` | Open decisions + index | This file |
| 01 | `01_PROTOCOL_NEUTRAL_MODEL.md` | Schema & value model (the source of truth) | Drafted |
| 02 | `02_WORKER_CONTRACT_AND_IPC.md` | `ProtocolDataSource` contract + supervisor⇄worker IPC | Drafted |
| 03 | `03_DOMAIN_MODEL.md` | Domain entities & relationships | Drafted |
| 04 | `04_DB_SCHEMA.md` | Postgres tables, value-timeline design, Flyway plan | Drafted |
| 05 | `05_API_CONTRACT.md` | REST/OpenAPI endpoints, SSE/WS, versioning | Drafted |
| 06 | `06_ARTIFACT_FORMATS.md` | Evidence, import/export, scenario, fault, synthetic | Drafted |
| 07 | `07_MODULE_STRUCTURE.md` | Gradle module layout, packages, boundary enforcement | Drafted |
| 08 | `08_AUTH_AND_MODES.md` | Auth, roles, concurrency, deployment modes | Drafted |
| — | `TASKS.md` | Backend task register + implementation status | Living |

Docs 03–08 depend on the decisions below and on docs 01–02. They are written
after these are approved, so a foundational reversal does not invalidate
downstream work.

## Open decisions requiring approval

Each item: context → options → **recommendation**. Approving the recommendations
unblocks the dependent specs.

### D1 — Supervisor⇄worker IPC transport

Context: `ARCHITECTURE.md` requires loopback-only, versioned IPC, contract
mismatch refused. `STACK.md` says workers are lean JVMs with "IPC layer only, no
Spring."

- Options: (a) **gRPC over loopback TCP** (protobuf, schema-first, bidirectional
  streaming, versioned via handshake); (b) raw TCP + length-prefixed protobuf;
  (c) stdio newline-JSON.
- **Recommendation: (a) gRPC over loopback.** Native streaming fits the value
  push and client/event streams; protobuf gives versioned contracts; Java
  tooling is mature. Adds `grpc-java` + `protobuf` — **new dependencies, needs
  STACK approval.**
- **Decision:** ✅ gRPC over loopback (selected). New deps `grpc-java`/`protobuf`
  are **approved in `STACK.md`**.

### D2 — Role model (⚠ doc inconsistency)

Context: `ARCHITECTURE.md` says authz roles are **admin, user**.
`UI_SCREEN_SPECS.md` uses **Admin/User** everywhere. `SPEC.md` "Assign User
Roles" (P2) lists **viewer/operator/editor/admin**.

- **Recommendation:** runtime authorization model = **admin / user** (matches the
  binding constraint and the UI). Treat viewer/operator/editor/admin as a future
  P2 product-level role expansion mapped onto the same enforcement points. This
  inconsistency should be resolved in the governance docs by the owner.
- **Decision:** ✅ Flexible permission model (selected). Internally one permission
  abstraction; externally expose **admin / user** now; expand to
  viewer/operator/editor/admin without changing enforcement points. The
  SPEC↔ARCHITECTURE/UI role wording is reconciled by clarifying `SPEC.md`
  (admin/user now; viewer/operator/editor/admin = P2 expansion).

### D3 — Object storage abstraction

Context: `ARCHITECTURE.md` requires an object-storage abstraction for large
artifacts; one build must serve local and shared modes.

- **Recommendation:** one `ObjectStore` port with two adapters — **filesystem**
  (local, default) and **S3-compatible** (shared). No blobs in Postgres. No new
  baseline dependency for the filesystem adapter; S3 SDK only if/when shared mode
  needs it.
- **Decision:** ✅ `ObjectStore` port + filesystem (default) & S3 adapters (selected).

### D4 — Optimistic concurrency mechanism

Context: `ARCHITECTURE.md` requires optimistic concurrency, no silent overwrite.

- **Recommendation:** monotonic `version` (bigint) column per editable entity;
  writes carry expected version; mismatch → `409 Conflict`. Exposed to the UI as
  an `ETag` / `If-Match` pair on mutating endpoints.

### D5 — Worker packaging & launch

Context: `STACK.md` — plain JVM default, optional AppCDS; jlink/jpackage for
self-contained local builds.

- **Recommendation:** worker = standalone runnable JAR launched as a child
  process by the supervisor (plain JVM + AppCDS). jlink/jpackage reserved for the
  self-contained local distribution, not the dev/default path.

### D6 — Live update transport

Context: `ARCHITECTURE.md` says SSE/WebSocket for live state and values.

- **Recommendation:** **SSE** for live values and runtime state (one-way,
  reconnect-friendly, proxy-friendly); reserve WebSocket only if a future surface
  needs client→server streaming. Start SSE-only.
- **Decision:** ✅ SSE-only at start (selected).

### D7 — API version base path

- **Recommendation:** path-based major versioning at `/api/v1/...`; additive
  within the major version (per `ARCHITECTURE.md`).

## Resolved-by-default (stated, not blocking)

- Determinism: explicit injectable clock + seeded RNG for generated value content
  and scenario step ordering only; client delivery timing is **not** guaranteed
  (consistent with `ARCHITECTURE.md`).
- Secrets/PKI: from env / external secret store only; never persisted in
  exportable artifacts (consistent with `ARCHITECTURE.md`).
