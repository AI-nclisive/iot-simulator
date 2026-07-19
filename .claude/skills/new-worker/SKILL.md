---
name: new-worker
description: >-
  Scaffold a new out-of-process protocol worker module. Use when adding support
  for a new protocol (e.g. MQTT, BACnet, EtherNet/IP): adding a protocol means
  adding a `workers/worker-<proto>` module that implements the gRPC
  ProtocolDataSource contract — never changing the supervisor. Invoke as
  `/new-worker mqtt`.
---

# Scaffold a new protocol worker

Owning specs: `backend-specs/02_WORKER_CONTRACT_AND_IPC.md` (contract/IPC) and
`07_MODULE_STRUCTURE.md` (module layout). Architecture rule (`ARCHITECTURE.md` →
Runtime model): **"Adding a protocol means adding a worker, not changing the
supervisor."** The supervisor stays protocol-agnostic; protocol-specific code lives
only in the worker.

**Input:** the protocol short name from the argument (e.g. `mqtt`). If none, ask.

## 0. Governance pre-check — STOP if a new dependency is needed

A new protocol almost always pulls in a new SDK. Per `STACK.md` / `ARCHITECTURE.md`
→ Governance, **no new dependency without explicit owner approval** — propose it
first (SDK, version, license; prefer Apache-2.0/permissive, matching Milo & j2mod).
Once approved, declare it **only** in `gradle/libs.versions.toml` (version + library
alias). Do not add the module until the dependency is approved.

The approved abstractions are the protocol-neutral model and the `ProtocolDataSource`
contract — do **not** add a new base class / generic abstraction without written
approval.

## 1. Create the module

`workers/worker-<proto>/build.gradle.kts` — mirror `workers/worker-modbus`:

```kotlin
plugins {
    id("buildlogic.java-conventions")
    application
}

description = "<Proto> protocol worker (<sdk>). Lean JVM, no Spring."

dependencies {
    implementation(project(":worker-contract"))

    implementation(platform(libs.grpc.bom))
    implementation(libs.grpc.stub)
    runtimeOnly(libs.grpc.netty.shaded)

    implementation(libs.<your.sdk.alias>)
}

application {
    mainClass = "com.ainclusive.iotsim.worker.<proto>.<Proto>WorkerMain"
}
```

Workers are **lean JVMs — no Spring**; optimize for memory and sustained throughput,
not startup.

## 2. Register the module

Add `"workers:worker-<proto>"` to the `include(...)` list in `settings.gradle.kts`
(dependencies flow downward; a worker depends on nothing but `:worker-contract`).

## 3. Implement the contract

Package `com.ainclusive.iotsim.worker.<proto>` (mirror `worker-opcua`):

- `<Proto>WorkerMain` — entry point; reads the loopback control port from `args[0]`,
  starts the gRPC server.
- `WorkerServer` — binds the gRPC `ProtocolDataSource` service on **loopback only**.
- `<Proto>ProtocolService` — implements the service RPCs (proto:
  `worker-contract/src/main/proto/iotsim/worker/v1/protocol_data_source.proto`):
  `Hello`, `Configure`, `Start`, `Stop`, `TestConnection`, `Scan`, `Capture`
  (stream), `ApplyValues` (stream), `ClientEvents`, `RuntimeEvents`.
- Projection: map the **protocol-neutral schema/value model ⇄ native address model**
  here — this is the only place that mapping exists. Recording-in (`Capture`) and
  live-out (`ApplyValues`) are distinct paths; keep them separate.
- Data types: implement a `<Proto>Types` module (mirror `worker-opcua`'s
  `OpcUaTypes.java`) as the single place housing this worker's native type ⇄
  neutral `DataType` mapping, default values, and to/from-native value
  conversion. `protocolmodel.DataType` is a **superset**, not an intersection
  every protocol must fill (backend-specs/01 §2) — a schema/recording is always
  scoped to one protocol, so a value only your protocol produces doesn't affect
  others. Default to reusing existing `DataType` values; only propose adding a
  new shared enum value when your protocol has a genuinely distinct value type
  worth first-class representation (the user needs to interact with it
  directly, and it's a bounded well-known type, not a general
  structure/variant) — that's a `protocol-model` change (`DataType.java` plus
  the compiler-enforced `ValueCodec.kindOf`/`SyntheticValueCoercion.coerce`
  exhaustive switches), not a worker-local decision. Anything else stays
  `null`/"unknown" at scan time for the user to resolve or exclude (IS-044
  pattern) — never silently coerced.

IPC is loopback-only and versioned; a mismatched contract version is refused, not
tolerated. **Do not touch `runtime-supervisor`** — it discovers and governs any
worker via the contract.

## 4. Tests

Add a gRPC contract test and a runtime IT mirroring
`workers/worker-opcua/src/test/...` (`OpcUaWorkerGrpcTest`, `OpcUaServerRuntimeIT`).

## 5. Build & verify

`./gradlew :workers:worker-<proto>:build` while iterating, then full `./gradlew
build` green before finishing ([[always-compile-and-test]]). Package for supervisor
mode with `:workers:worker-<proto>:installDist`.
