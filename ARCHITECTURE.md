# Architecture

Change rule: do not change this file without explicit user approval. Propose changes first with rationale and expected impact.

## Document Metadata

- Status: Active.
- Last updated: 2026-06-18.
- Version: 2.1.
- Related documents:
  - `SPEC.md`: source of truth for product capabilities.
  - `DESIGN.md`: MVP UI design.
  - `MEMORY.md`: shared glossary and durable notes.

### Relationship To Other Documents

`ARCHITECTURE.md` describes the target product architecture. `DESIGN.md` describes the MVP UI, which is a deliberate subset of that target. Where the two differ, the difference is intentional and scoped to the MVP:

- Access mode: the target product supports both trusted local no-login usage and shared login-based usage. The MVP UI is shared-login-first; local no-login usage is kept future-compatible but is not the MVP priority.
- Roles: the target product supports viewer, operator, editor, and admin roles. The MVP ships with two roles, Admin and User (see Decision 15).

These are not contradictions; they are staged adoption of the broader architecture.

## Drivers

- Simulate OPC UA and Modbus TCP data-sources for edge-device testing.
- Record, replay, and generate deterministic data for scenarios and regression flows.
- Observe live values, clients, health, runtime events, faults, errors, and export evidence.
- Support local no-login usage and shared team usage with login, roles, and external identity providers.
- Run on Linux, Windows, and macOS.

## Architecture Overview

The system is a modular monolith built on C#/.NET and ASP.NET Core, fronted by a React/TypeScript Web UI. A single backend process hosts the application modules and exposes REST/OpenAPI for commands, queries, and automated-test control, plus SSE/WebSocket for live runtime updates.

Protocol data-sources do not run inside the backend process. A runtime supervisor inside the backend manages each data-source as an isolated out-of-process worker (see Decision 10), giving fault isolation aligned with the fault-simulation feature set. Persistence uses PostgreSQL for relational product data and TimescaleDB for value timelines, with an object-storage abstraction for large artifacts and evidence bundles.

The architecture optimizes for one core risk: a reliable industrial protocol simulator runtime. Standard CRUD concerns are secondary to runtime fidelity, fault isolation, determinism, and reproducible evidence.

## Modules

The modular monolith is organized into the following internal modules. Dependencies flow toward shared/lower-level modules; protocol and runtime modules must not depend on UI-facing modules.

1. Protocols: OPC UA and Modbus TCP server/client implementations, schema/address-space modeling, and protocol-specific encodings and validation.
2. Projects: project lifecycle, data-source definitions, schema persistence, and import/export.
3. Recordings: recording capture, samples, and replay assignment against data-sources.
4. Scenarios: deterministic scenario engine (explicit clock, seeded random), step model, and runtime event timelines.
5. Observability (product): runtime events, live values, client visibility, health, and errors as product data.
6. Evidence: evidence capture, bundling, and export.
7. Access Control: authentication, authorization, roles, and identity-provider integration.
8. Runtime Supervisor (cross-cutting runtime): data-source lifecycle, out-of-process worker management, port allocation, process isolation boundaries, and graceful start/stop.
9. Platform (cross-cutting technical): configuration, secrets access, storage abstraction, technical observability (OpenTelemetry, health checks, metrics, structured logs), and persistence/migration plumbing.

## Use

1. Use C# on .NET 10 LTS for backend and simulator runtime.
2. Use ASP.NET Core as the backend application framework.
3. Use a modular monolith with clear internal modules for protocols, projects, recordings, scenarios, observability, evidence, and access control.
4. Use REST plus OpenAPI for commands, queries, and automated-test control.
5. Use Server-Sent Events or WebSocket for live runtime updates.
6. Use the OPC Foundation UA .NET Standard Stack for OPC UA client/server functionality.
7. Use NModbus for Modbus TCP client/server functionality.
8. Use PostgreSQL as the primary database.
9. Use TimescaleDB where high-volume value timelines need time-series optimization.
10. Use DbUp for SQL migrations, Npgsql for PostgreSQL access, and Dapper for explicit SQL.
11. Use React, TypeScript, Vite, React Router, TanStack Query/Table, Zustand, Radix UI, and Tailwind CSS for the Web UI.
12. Use ASP.NET Core authentication/authorization with OAuth2/OIDC, Docker Compose, OCI containers, xUnit, Testcontainers, Vitest, Testing Library, and Playwright for security, runtime packaging, and testing.
13. Use the project glossary as the shared domain language: data-source, edge-device, project, recording, sample, replay, scenario, fault, and evidence.
14. Use versioned schemas for projects, data-source definitions, recordings, samples, scenarios, and evidence exports.
15. Use a deterministic scenario engine with an explicit clock, seeded random sources, and persisted runtime event timelines.
16. Use a runtime supervisor for data-source lifecycle, port allocation, process isolation boundaries, and graceful start/stop behavior.
17. Use local filesystem artifact storage for local mode and an S3-compatible object storage abstraction for shared environments.
18. Use OpenTelemetry, ASP.NET Core health checks, .NET metrics, and structured logs for technical observability, while keeping runtime events as product data.

## Do Not Use

1. Do not use Node.js/NestJS as the primary simulator runtime unless the protocol strategy changes.
2. Do not split into microservices before scale or ownership boundaries require it.
3. Do not make Kubernetes the baseline local or MVP deployment target.
4. Do not introduce a second primary database before PostgreSQL/TimescaleDB limits are proven.
5. Do not target Java/JVM as the primary simulator runtime without explicit approval.
6. Do not store secrets, identity-provider client secrets, certificates, or generated private keys in repository files or exported artifacts unencrypted.

## Rationale

The product's core risk is a reliable industrial protocol simulator runtime, not standard CRUD. C#/.NET is selected because the first OPC UA scope includes all listed feature groups, and the official OPC Foundation UA .NET Standard Stack provides the strongest fit for broad OPC UA client/server behavior, security, subscriptions, methods, historical access, alarms/events, and industrial interoperability. ASP.NET Core adds production-ready APIs, OpenAPI, health checks, metrics, configuration, and OIDC support. PostgreSQL plus optional TimescaleDB covers both relational product data and timestamped value timelines without splitting persistence too early. React and TypeScript fit an operational Web UI with dense state, live views, tables, and evidence inspection.

## Runtime Supervisor And Worker Model

This view elaborates module #8 (Runtime Supervisor) and Decision 10 (out-of-process data-sources). The supervisor lives inside the backend process; each data-source runs as an isolated worker process that owns one protocol endpoint.

### Responsibilities Split

Supervisor (in backend):

- Worker lifecycle: launch, start, stop, drain, restart, and force-kill.
- Desired-state reconciliation: persist desired data-source state and drive actual state toward it.
- Health and liveness: track heartbeats, readiness, and last-known state per worker.
- Port ownership: allocate a port from the pre-mapped range (Decision 11) at launch and reclaim it on stop.
- IPC management: own the control-plane and data-plane channels to each worker.
- Resource governance: enforce the maximum worker count, apply per-worker resource limits, and apply backpressure on inbound value streams.
- Fault coordination: translate scenario/operator fault commands into worker-level or process-level faults and record their intent.
- Crash detection and recovery: distinguish real failures from intentional faults and apply the recovery policy accordingly.
- Aggregation: collect product runtime events, value changes, and client-connection events from workers and hand them to the relevant modules.

Worker (per data-source process):

- Host exactly one protocol endpoint (OPC UA via the OPC Foundation stack, or Modbus TCP via NModbus).
- Serve the address space / node space to connected edge-devices on its owned port.
- Apply the current data behavior: static values, synthetic generation, replay, or scenario-driven values.
- Apply protocol-level faults on command (bad value, delay, protocol error, dropped subscription, refused connection).
- Emit the complete value-change stream plus runtime and client events to the supervisor.
- Respond to control commands and report state transitions and acknowledgements.

### Control Plane And Data Plane

The supervisor-worker contract separates control from data:

- Control plane (request/response): start, stop, drain, configure, set data behavior, apply fault, clear fault, replay control, and health probe. Each command is acknowledged with the resulting worker state.
- Data plane (worker to supervisor stream): value changes, runtime events (source start/stop, client connect/disconnect, replay/scenario step changes, faults, errors), and client-session updates.
- Heartbeat: periodic liveness signal independent of data activity, so an idle worker is still observably alive.

Workers always emit the complete value-change stream over IPC. The backend, not the worker, fans this out into the complete recording path (Decision 3) and the conflated/throttled live path (Decision 17). This keeps recording completeness and determinism anchored at the source while the live view stays cheap.

### Worker Lifecycle And States

Supervisor-tracked worker states map onto the UI runtime states (Stopped, Starting, Running, Stopping, Error, Disabled):

- NotStarted: defined but no process.
- Launching: process starting and establishing IPC; maps to Starting.
- Ready/Running: protocol endpoint serving; maps to Running.
- Draining/Stopping: refusing new clients, flushing recording buffers, releasing the port; maps to Stopping.
- Stopped: process exited intentionally.
- Faulted: process exited or became unhealthy unexpectedly; maps to Error.

A reconciler loop compares desired state to actual state and issues the commands needed to converge, applying a restart policy with exponential backoff on unexpected exits.

### Fault Handling Versus Real Failure

Fault simulation is a product feature, so the supervisor must not "heal" an intentional fault (Decision 20):

- Protocol-level faults (bad value, delay, timeout, protocol error) are injected inside the running worker and do not change its lifecycle state.
- Process-level simulated faults (unavailable data-source, simulated crash) stop or kill the worker on purpose; the supervisor records the intent and does not auto-restart.
- Real failures (unexpected crash, lost heartbeat, IPC loss) trigger the recovery policy: surface an Error state and a runtime event, then restart with backoff up to a retry limit.

Every stop/kill therefore carries an intent tag (operator, scenario/fault, or unexpected) so recovery behavior is correct and the event timeline is accurate.

### Graceful Start And Stop

- Start: allocate a port, launch the process, wait for IPC readiness, then transition to Running; on readiness timeout, mark Faulted and apply the recovery policy.
- Stop: send drain, stop accepting new clients, flush pending recording writes, release the port, then expect a clean exit; on drain timeout, force-kill and record the forced stop.

### Resource Governance And Isolation

- The supervisor caps concurrent workers against the NFR baseline (~50) and rejects launches beyond the cap with a clear error.
- Per-worker soft resource limits and inbound-stream backpressure protect the backend from a noisy worker.
- A worker crash cannot take down the backend; that isolation is the primary reason for the out-of-process model.
- If the backend dies, workers detect the lost parent and self-terminate to avoid orphaned processes holding ports.

### Security And Transport

- IPC channels are local-only (loopback or an OS local transport) and never exposed externally.
- Workers run with least privilege; secrets and OPC UA certificate material are passed by reference to environment/secret-store entries, not as files or inline payloads (Decision 14).
- The supervisor and workers share a versioned IPC contract; on version mismatch the worker is refused and the condition is surfaced rather than silently tolerated.

## Deployment View

The baseline deployment is Docker Compose for both local and shared usage (Decision 1). Kubernetes is explicitly not the MVP target (Do Not Use #3).

Containers:

- Web UI (static assets served to the browser).
- Backend (ASP.NET Core; hosts modules and the runtime supervisor).
- PostgreSQL (relational product data).
- TimescaleDB (value timelines; may be the same PostgreSQL instance with the TimescaleDB extension, or a dedicated instance).
- Object storage (local: filesystem volume; shared: MinIO or external S3-compatible store).
- Identity provider (optional; shared environments only).

Out-of-process data-source workers (Decision 10) are launched and managed by the supervisor inside the backend container in the MVP. Each worker owns its protocol endpoint.

Protocol port exposure under Docker Compose uses a static, pre-mapped port range (Decision 11). The supervisor allocates from this declared range so external edge-devices reach data-sources through known, pre-published container port mappings. A gateway/multiplexer in front of sources may be considered later but is out of MVP scope.

Topology notes:

- Local mode: single host, filesystem object storage, no identity provider, optional no-login (target capability).
- Shared mode: single host or small VM, MinIO/external S3, identity provider enabled, login required.
- High availability and horizontal scale-out are out of scope for the first version (Decision 2).

## Data Model And Persistence

Persistence is split by data shape rather than by module:

- PostgreSQL (relational): projects, data-source definitions, schemas, scenario definitions, recording/sample metadata, evidence metadata, users, roles, and runtime event records that must be queried relationally.
- TimescaleDB (time-series): value-change timelines for both recordings and live/observed values, stored as hypertables (Decision 12).
- Object storage (blobs): exported evidence bundles, exported project/recording artifacts, and any large binary content (Decision 8). Local mode backs the abstraction with the filesystem; shared mode uses MinIO or external S3.

Persistence rules:

- Recordings capture every value change (Decision 3); the recording path performs no sampling and must not drop values.
- Value-timeline ingestion uses batched writes with backpressure to absorb bursts (Decision 12).
- Retention and compression policies are configurable for value timelines (TimescaleDB compression after a configurable age, deletion after a configurable retention window).
- All exportable/importable artifacts carry a versioned envelope to support cross-version import (Decision 16).
- Schemas for projects, data-source definitions, recordings, samples, scenarios, and evidence exports are versioned (Use #14).

## Security

Security spans authentication, authorization, secrets, and protocol PKI.

- Authentication: ASP.NET Core authentication with OAuth2/OIDC. Shared environments integrate external identity providers (for example Keycloak, AWS Cognito, Azure Entra ID). Local no-login usage is a target capability for trusted scenarios.
- Authorization: role-based. The target model is viewer/operator/editor/admin; the MVP uses Admin/User (Decision 15). Roles are global to an environment in the first version; per-project scoping is deferred.
- Transport: TLS for the Web UI and API; OPC UA security modes/policies supported by the OPC UA module.
- Secrets: never stored in repository files or exported artifacts unencrypted (Do Not Use #6). Secrets, identity-provider client secrets, and credentials are supplied via environment variables / an external secret store (Decision 14), not committed files.
- OPC UA PKI: an explicit trust model with application certificates, a trust list/trust store, rejected-certificate handling, and certificate rotation. PKI material lives outside version control and outside exports.
- Exports: evidence, project, and recording exports exclude secrets and private keys by default. If certificate material must be included, the bundle is encrypted.

## Non-Functional Requirements (Initial MVP Targets)

These targets follow Decision 2 (small-team scale, low update rate) and are initial values to validate against real usage, not hard commitments.

- Concurrency: tens of data-sources per environment (design baseline up to ~50) and tens of connected edge-device clients.
- Update rate: low; design baseline on the order of a few to ~10 value changes per second aggregate per source, with bursts absorbed by batched ingestion.
- Live latency: value changes visible in live views within roughly 250-500 ms under normal load.
- Live refresh: live streams conflated/throttled to roughly 4-10 Hz per node (Decision 17), independent of the complete recording path.
- Recording integrity: every value change persisted with no sampling loss on the recording path (Decision 3).
- Availability: single-node, no HA in the first version; planned maintenance windows are acceptable.
- Recovery: backup-based recovery; RPO bounded by backup interval, RTO bounded by restore time; no hot standby in the first version.
- Portability: runs on Linux, Windows, and macOS via OCI containers.
- Determinism: guaranteed for generated value content and scenario step ordering (Decision 13).

## Decisions

1. Local development and QA usage should run through Docker Compose.
   - Rationale: expected users can handle Docker, and Docker Compose keeps backend, UI, PostgreSQL, TimescaleDB, and optional identity provider reproducible.
   - Impact: no separate no-Docker desktop-style local package is required for the first version.

2. The first shared environment should target small-team usage.
   - Rationale: the initial target is a few users, a few projects, tens of data-sources, and a low update rate.
   - Impact: the modular monolith and single PostgreSQL/TimescaleDB persistence model remain appropriate; scale-out can wait until real usage proves the need.

3. Recordings should store every value change.
   - Rationale: exact bug reproduction is more important than minimizing storage in the first architecture.
   - Impact: recording storage, replay, evidence export, and retention policies must be designed for complete value-change timelines.

4. Automated tests should control the simulator through HTTP/OpenAPI only.
   - Rationale: HTTP/OpenAPI is language-neutral and works well for CI pipelines, backend tests, frontend tests, and external edge-device test suites.
   - Impact: no CLI or language-specific SDK is required for the first version.

5. Evidence exports should be combined ZIP artifacts.
   - Rationale: one portable artifact can serve both machines and humans by including JSON/CSV data plus an HTML summary.
   - Impact: evidence export schemas should be stable, and the HTML summary should be generated from the same data included in the ZIP.

6. The first OPC UA scope should include all listed OPC UA feature groups.
   - Scope: basic nodes and reads, subscriptions, security certificates and policies, methods, historical access, and alarms/events.
   - Rationale: the simulator should be useful against realistic edge-device integrations from the beginning, not only simple polling clients.
   - Impact: OPC UA implementation must be designed in slices, but the architecture should not assume a minimal read-only OPC UA server.

7. The first Modbus TCP scope should include all listed Modbus TCP feature groups.
   - Scope: common register reads, register writes, coils, discrete inputs, required function codes, and target-device data encodings.
   - Rationale: the simulator should cover both read-only monitoring and command/setpoint workflows.
   - Impact: Modbus schemas must model address spaces, read/write behavior, boolean signals, numeric encodings, and validation rules explicitly.

8. Shared-environment artifacts use an object-storage abstraction, defaulting to the local filesystem.
   - Decision: introduce the S3-compatible storage abstraction now; back it with the local filesystem for local/MVP usage and MinIO or external S3 for shared environments.
   - Rationale: large recordings and complete value-change timelines (Decision 3) do not belong in PostgreSQL (table bloat, heavy backups). The abstraction now avoids a costly migration later.
   - Impact: all artifact read/write paths go through the abstraction; the only difference between modes is the backing store.

9. The OPC Foundation UA .NET Standard Stack is used under internal/SaaS, non-distribution terms.
   - Decision: the product is an internal/SaaS tool that is not distributed to customers; the OPC UA stack is used on that basis.
   - Rationale: the GPLv2/RCL dual license is primarily a constraint on distribution. Without distribution, the licensing risk that previously blocked the entire OPC UA scope is removed.
   - Impact: if the distribution model ever changes (shipping the product to customers), this decision must be revisited and may require OPC Foundation membership, a commercial agreement, or an alternative stack.

10. Each data-source runs out-of-process under the runtime supervisor.
    - Decision: the supervisor manages each data-source as an isolated worker process (per source, with pooling granularity to be refined).
    - Rationale: out-of-process execution provides strong fault isolation aligned with the runtime supervisor and fault-simulation features; a hung or crashed protocol server cannot take down the whole monolith.
    - Impact: the supervisor must own worker lifecycle, IPC, health, port ownership, and resource governance; this is more complex than in-process but matches the product's fault-isolation goals.

11. Edge-devices reach protocol ports through a static, pre-mapped port range under Docker Compose.
    - Decision: allocate protocol ports from a declared, pre-mapped range; a gateway/multiplexer may be considered later.
    - Rationale: host networking is effectively Linux-only and would break the Linux/Windows/macOS portability driver; a pre-mapped range is predictable and cross-platform.
    - Impact: port allocation must account for container port mapping and external reachability; the available range is a deployment configuration value.

12. Value timelines live in TimescaleDB; evidence bundles live in object storage.
    - Decision: store recorded and observed value timelines in TimescaleDB hypertables with batched writes and backpressure; store exported evidence bundles in object storage; apply configurable retention and compression to timelines.
    - Rationale: reconciles "store every value change" (Decision 3) with "low update rate" (Decision 2) by keeping ingestion efficient and growth survivable.
    - Impact: ingestion needs batching/backpressure; retention and compression thresholds are configuration; evidence export reads from the timeline store and writes bundles to object storage.

13. Determinism is guaranteed for value content and scenario ordering, not for client delivery timing.
    - Decision: the determinism guarantee covers generated value content and scenario step ordering on the simulator clock (explicit clock, seeded random); network delivery timing and external client poll/subscription timing are explicitly out of the guarantee.
    - Rationale: external live clients run on wall-clock time and control their own request timing, which the simulator cannot make deterministic.
    - Impact: documentation and tests must scope determinism to values and scenario timeline; replay uses simulation-time stamps.

14. Secrets are supplied via environment variables / an external secret store.
    - Decision: secrets, identity-provider client secrets, and credentials come from environment variables or a secret store; OPC UA PKI uses an explicit trust list/trust store with rotation; exports exclude secrets and private keys by default, encrypting any unavoidable certificate material.
    - Rationale: gives the "Do Not Use" secrets rule a concrete mechanism.
    - Impact: no secret material in repository files or exported artifacts; deployment must provide the required environment/secret-store configuration.

15. Roles are global to an environment; the MVP ships Admin and User.
    - Decision: roles are environment-global in the first version; per-project role scoping is deferred. The MVP uses Admin/User, aligning with `DESIGN.md`; the target viewer/operator/editor/admin model remains future-compatible. Data created in local no-login mode is owned by a local identity and reassigned to the importing user when imported into a shared environment.
    - Rationale: matches MVP scope and the UI design while leaving room for richer scoping later.
    - Impact: authorization checks are environment-level; import flow must reassign ownership.

16. Import/export uses versioned artifact envelopes with upgrade-on-import.
    - Decision: every project, recording, sample, and evidence artifact carries a schema-versioned envelope; import upconverts older versions to the current version through documented migrators and rejects versions newer than supported with a clear message.
    - Rationale: artifacts created in one product version may be imported into another, especially for bug-report handoff.
    - Impact: a maintained migration chain is required; newer-than-supported artifacts fail safely.

17. Live streaming and full recording use separate pipelines.
    - Decision: the recording path captures every change (Decision 3); the live path (SSE/WebSocket) uses server-side latest-value-wins conflation with a capped UI refresh rate (~4-10 Hz per node).
    - Rationale: live observation and complete recording have different needs; an unthrottled live stream can flood the UI.
    - Impact: two distinct data paths; live sampling/throttling is independent of the recording path.

18. The REST/OpenAPI test-control contract uses path-based major versioning.
    - Decision: major version in the path (for example `/api/v1`), additive evolution within a major version, OpenAPI as the contract, and a deprecation policy with sunset headers and an overlap window.
    - Rationale: automated tests depend on the HTTP/OpenAPI contract (Decision 4); explicit versioning prevents silently breaking consumers' CI flows.
    - Impact: breaking changes require a new major version and a deprecation period.

19. The supervisor-worker IPC contract is versioned and runs over a local-only transport.
    - Decision: control plane uses request/response commands with state acknowledgements; the data plane uses a worker-to-supervisor stream for value changes and runtime/client events; a periodic heartbeat signals liveness. The contract carries a version, and the transport is local-only (loopback or an OS local channel), never externally exposed.
    - Rationale: separating control from data keeps commands responsive while value/event volume flows on a dedicated channel; explicit versioning prevents silent incompatibility across deploys.
    - Impact: both backend and worker must honor the same contract version; mismatched workers are refused; the IPC channel is not an external attack surface.

20. Intentional faults are distinguished from real failures for recovery.
    - Decision: every worker stop/kill carries an intent tag (operator, scenario/fault, or unexpected). Protocol-level faults are injected in-process without changing lifecycle state; process-level simulated faults stop the worker on purpose and are not auto-restarted; only unexpected failures trigger the restart policy with exponential backoff.
    - Rationale: fault simulation is a product feature, so the supervisor must not "heal" an intentional fault, while still recovering from genuine crashes.
    - Impact: the runtime event timeline records intent accurately; recovery logic keys off intent rather than process exit alone.

## Open Questions

The previously open questions are now resolved as Decisions 8-20. The following are deferred follow-ups, not blocking for the MVP:

1. Out-of-process granularity: per-source workers (current default) vs pooled workers, and the detailed resource-governance limits under the supervisor (Decisions 10, 19).
2. Concrete retention and compression thresholds for value timelines, validated against real recording sizes (Decision 12).
3. Secret-store choice for shared environments and the OPC UA certificate rotation procedure (Decision 14).
4. Timing and triggers for adopting per-project role scoping and the broader viewer/operator/editor/admin model (Decision 15).
5. If the distribution model changes, the OPC UA stack licensing path must be reopened (Decision 9).
