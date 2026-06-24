# IoT Data Source Simulator — Backend

Modular-monolith backend (Java 25 + Spring Boot) with out-of-process protocol
workers. See the design docs at the repo root (`SPEC.md`, `ARCHITECTURE.md`,
`STACK.md`), the UI docs under `frontend/docs/` (`DESIGN.md` and others), and the
implementation specs in `backend-specs/`.

## Module map

Dependencies flow downward only (`backend-specs/07_MODULE_STRUCTURE.md`):

| Module | Role |
| --- | --- |
| `protocol-model` | Protocol-neutral schema & value model (shared kernel) |
| `worker-contract` | `ProtocolDataSource` contract + gRPC IPC |
| `platform` | Cross-cutting ports (object store, clock, ids, secrets) |
| `persistence` | Flyway migrations, jOOQ, repositories |
| `domain` | Projects, schemas, recordings, scenarios, faults, evidence |
| `runtime-supervisor` | Worker lifecycle, IPC, health, ports |
| `api` | REST/OpenAPI + SSE; authz enforcement |
| `app` | Spring Boot bootstrap |
| `workers/worker-opcua` | OPC UA worker (Eclipse Milo), lean JVM |
| `workers/worker-modbus` | Modbus TCP worker (j2mod), lean JVM |

## Build & test

```bash
./gradlew build
```

Runs on JDK 25 (toolchain). Gradle itself may run on JDK 17+ or 25.

## Run

Requires Postgres (connection externally configured via env):

```bash
docker compose up --build
```

The API serves under `/api/v1`; actuator health at `/actuator/health`.

## Project tracking

- **Board:** [IoT Simulator Backend](https://github.com/orgs/AI-nclisive/projects/1) —
  live status by `IS-XXX` / `Area` (Todo / In Progress / In review / Done).
- **Task catalog:** [`backend-specs/TASKS.md`](backend-specs/TASKS.md) — the source list of `IS-XXX` task IDs.

## Notes

- gRPC/protobuf code is generated from `worker-contract/src/main/proto`.
- jOOQ code generation runs against a Flyway-migrated Testcontainers Postgres and
  is a separate task (kept off the default build so `build` stays offline).
