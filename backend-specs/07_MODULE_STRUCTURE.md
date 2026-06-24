# Module Structure & Build Layout (DRAFT)

Status: **DRAFT — proposal for approval.** Concrete Gradle (Kotlin DSL)
multi-module layout realizing the `ARCHITECTURE.md` module map and runtime model.
Honors the governance rules: dependencies flow downward only; protocol/runtime
modules must not depend on UI-facing modules; no new base class or generic
abstraction without approval (the protocol-neutral model and `ProtocolDataSource`
contract are the only approved abstractions).

## Gradle modules

```
iot-simulator/
├── settings.gradle.kts
├── build-logic/                  # convention plugins (java, spotless, jooq, test)
├── protocol-model/               # the protocol-neutral schema & value model (shared kernel)
├── worker-contract/              # ProtocolDataSource contract + gRPC/proto (D1)
├── platform/                     # cross-cutting: auth, secrets, object-store port, clock, ids
├── persistence/                  # Flyway migrations, jOOQ generated code, repositories
├── domain/                       # projects, schemas, recordings, samples, scenarios,
│                                 #   faults, evidence, observability — built on protocol-model
├── runtime-supervisor/           # worker lifecycle, IPC client, health, ports, governance
├── api/                          # REST/OpenAPI (springdoc) + SSE; authz enforcement
├── app/                          # Spring Boot bootstrap; wires api+domain+supervisor+persistence
├── workers/
│   ├── worker-opcua/             # Eclipse Milo; lean JVM, NO Spring
│   └── worker-modbus/            # j2mod; lean JVM, NO Spring
└── (web/                         # React/TS app — separate FE build, out of backend scope)
```

## Dependency direction (enforced)

Lower may not depend on higher; arrows = allowed `implementation` deps.

```
app ─→ api ─→ domain ─→ persistence ─→ platform
                 │            │            │
                 └────────────┴───→ protocol-model
runtime-supervisor ─→ worker-contract ─→ protocol-model
runtime-supervisor ─→ platform
api ─→ runtime-supervisor
workers/* ─→ worker-contract ─→ protocol-model   (workers depend on NOTHING else)
```

Hard rules:
- `protocol-model` depends on nothing (pure kernel).
- `workers/*` depend only on `worker-contract` (+ its `protocol-model`) and their
  protocol SDK. **No Spring, no domain, no persistence** in workers (`STACK.md`:
  lean JVMs).
- `runtime-supervisor` is protocol-agnostic: it depends on `worker-contract`, not
  on any concrete worker (workers are launched as external processes — D5).
- `domain`, `persistence`, `runtime-supervisor` never depend on `api`/`app`.

## Boundary enforcement

- Gradle module graph is the primary boundary (a forbidden dep won't compile).
- Add **ArchUnit** tests in CI to assert package/layer rules within modules
  (e.g. domain must not import api packages). ArchUnit is test-scope only and is
  approved in `STACK.md` (used alongside the Gradle module graph).

## Package naming

Root package `com.epam.iotsim` (chosen default; renameable later). Per module:
`…iotsim.protocolmodel`,
`…iotsim.workercontract`, `…iotsim.domain.<area>` (projects, schemas, recordings,
scenarios, faults, evidence, observability), `…iotsim.supervisor`,
`…iotsim.persistence`, `…iotsim.api.<area>`, `…iotsim.worker.opcua`,
`…iotsim.worker.modbus`, `…iotsim.platform.<concern>`.

## Build conventions

- Gradle Kotlin DSL; shared config via `build-logic` convention plugins
  (toolchain = **Java 25 LTS**), versions in a single version catalog
  (`gradle/libs.versions.toml`).
- `worker-contract` runs protobuf/gRPC codegen (D1, pending STACK approval).
- `persistence` runs Flyway then jOOQ codegen against a Testcontainers Postgres
  in build.
- Workers build as standalone runnable JARs (D5), with AppCDS; jlink/jpackage
  reserved for the self-contained local distribution.
- Deploy: Docker Compose (`STACK.md`) — backend + Postgres (+ optional object
  store), DB connection externally configured via env.

## Testing layout

- `domain`/`persistence`: JUnit 5 + AssertJ + Testcontainers (Postgres).
- `runtime-supervisor`/`workers`: contract tests against `worker-contract`;
  worker integration tests start a real worker process and a protocol client.
- `api`: slice/integration tests against the running app.

## Resolved

- ✅ Baseline **Java 25 LTS**.
- ✅ Root package **`com.epam.iotsim`** (renameable later).
- ✅ **ArchUnit** approved (test-scope) for boundary enforcement, alongside the
  Gradle module graph.

No open questions remain in this document.
