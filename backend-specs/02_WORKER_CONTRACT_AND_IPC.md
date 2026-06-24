# ProtocolDataSource Worker Contract & Supervisor⇄Worker IPC (DRAFT)

Status: **DRAFT — proposal for approval.** Defines the second approved
abstraction from `ARCHITECTURE.md` — the single `ProtocolDataSource` worker
contract — and the IPC between the runtime supervisor and out-of-process workers.
Depends on decision **D1 (gRPC over loopback)** and **D5 (worker packaging)** in
`00_DECISIONS_AND_INDEX.md`.

## 1. Roles

- **Supervisor** (in backend, Spring): owns worker lifecycle — spawn, configure,
  start/stop, health, restart-with-backoff, port allocation, resource
  governance. Protocol-agnostic. Adding a protocol must not change the
  supervisor.
- **Worker** (out-of-process, lean JVM, no Spring): implements one protocol via
  its SDK (Milo / j2mod) behind the `ProtocolDataSource` contract.

One worker process = one running data-source instance, so a crash is isolated
(`ARCHITECTURE.md`).

## 2. Transport

- **gRPC over loopback TCP only** (127.0.0.1), never bound to a public
  interface. Supervisor allocates an ephemeral loopback control port per worker
  and passes it at launch.
- The protocol listen port (OPC UA / Modbus the Edge Device connects to) is
  separately allocated by the supervisor and passed in `Configure`.
- **Versioned handshake:** every worker reports `contractVersion` (semver) on
  `Hello`; the supervisor refuses a mismatched major version (no tolerant
  fallback, per `ARCHITECTURE.md`).
- New dependencies (`grpc-java`, `protobuf`) — pending STACK approval (D1).

## 3. The `ProtocolDataSource` contract

Conceptual gRPC service (final `.proto` produced after approval). Streaming is
used so values and events flow continuously.

| RPC | Direction | Purpose |
| --- | --- | --- |
| `Hello` | sup→wkr | Handshake; exchange contract version, protocol id, capabilities. |
| `Configure` | sup→wkr | Provide schema (neutral model), protocol bindings, listen port, runtime options. |
| `Start` | sup→wkr | Begin serving the protocol endpoint. |
| `Stop` | sup→wkr | Stop serving; keep process alive for reuse or shutdown. |
| `ApplyValues` | sup→wkr (stream) | Push neutral value batches to project onto the protocol address space (replay/synthetic/live). |
| `ClientEvents` | wkr→sup (stream) | Client connect/disconnect, subscription activity. |
| `RuntimeEvents` | wkr→sup (stream) | Source start/stop, errors, fault state changes. |
| `InjectFault` | sup→wkr | Activate/clear a fault (neutral or protocol-specific). |
| `Health` | sup→wkr | Liveness/readiness + resource snapshot. |
| `Shutdown` | sup→wkr | Graceful process exit. |

### Capabilities

`Hello` returns a capability set (e.g. supported data types, supported fault
kinds, max nodes) so the supervisor/domain can validate a schema before
configuring a worker and the UI can warn early.

## 4. Lifecycle & fault policy

States: `SPAWNED → READY (Hello ok) → CONFIGURED → RUNNING → STOPPED → EXITED`.

- **Intentional faults are never auto-healed** — they are a product feature,
  tagged by intent (`ARCHITECTURE.md`). Only **unexpected** worker failure
  triggers **restart-with-backoff**.
- On unexpected exit: supervisor restarts with exponential backoff up to a cap,
  emits a runtime event, and reflects health state to the API/UI.
- `Stop` is graceful; forced kill only after a timeout.

## 5. Port allocation & resource governance

- Loopback control port: ephemeral, supervisor-assigned, released on exit.
- Protocol listen port: from a configured range (or explicit per source);
  conflicts surfaced as a configuration error before `Start`.
- Resource governance: supervisor enforces caps on concurrent workers and may
  refuse new starts under pressure (supports SPEC "Run Multiple Data Sources
  Concurrently").

## 6. Value flow (recording vs live)

- **Live/replay/synthetic out**: supervisor → `ApplyValues` stream → worker
  projects onto protocol address space.
- **Recording in**: when recording a *real* source, the scan/record path reads
  the real endpoint (a worker may act as client, or a dedicated reader) and
  produces neutral values into the recording timeline. The two data paths
  (full-fidelity recording vs conflated live) stay distinct per
  `ARCHITECTURE.md`.

## 7. What lives where

- Protocol-specific code (Milo/j2mod, address mapping, protocol faults): **only**
  in workers.
- Lifecycle, IPC, health, ports, governance: **only** in the supervisor.
- Neutral schema/value model + domain semantics: in domain modules, passed to
  workers via `Configure`/`ApplyValues`.

## Open questions for reviewer

- Approve gRPC + protobuf as new dependencies (D1).
- Confirm one-process-per-source (vs. a worker hosting multiple sources). Default
  proposal: one source per process for clean isolation.
- Confirm whether real-source reading during scan/record is done by a worker in
  "client mode" or a separate in-backend reader.
