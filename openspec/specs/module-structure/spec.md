# module-structure Specification

## Purpose
The Gradle multi-module layout and its enforced dependency direction are how
the project keeps protocol-specific code out of the supervisor and
domain/persistence code out of the workers, without relying on convention
alone.
## Requirements
### Requirement: Module layout
The build SHALL be organized as: `protocol-model` (protocol-neutral schema/
value model, depends on nothing), `worker-contract` (the `ProtocolDataSource`
gRPC contract), `platform` (cross-cutting: auth, secrets, object-store port,
clock, ids), `persistence` (Flyway + jOOQ generated code + repositories),
`domain` (projects, schemas, recordings, samples, scenarios, evidence,
observability), `runtime-supervisor` (worker lifecycle, IPC client, health,
ports, governance), `api` (REST/OpenAPI + SSE, authz enforcement), `app`
(Spring Boot bootstrap wiring the above), and `workers/worker-<proto>` (one
module per protocol, e.g. `worker-opcua`, `worker-modbus`). The frontend is a
separate npm/Vite build (source in `frontend/src`), out of the Gradle build
entirely.

#### Scenario: New module registered in settings.gradle.kts
- **WHEN** a new Gradle module is added under the layout above
- **THEN** it is listed in `settings.gradle.kts`'s `include(...)` and follows
  the naming/package convention of its category (e.g. `workers:worker-<proto>`)

### Requirement: Dependency direction is enforced downward only
`protocol-model` SHALL depend on nothing. `workers/*` SHALL depend only on
`worker-contract` (and transitively `protocol-model`) plus their protocol
SDK - no Spring, no domain, no persistence in a worker. `runtime-supervisor`
SHALL depend on `worker-contract`, never on a concrete worker module (workers
run as external processes). `domain`, `persistence`, and `runtime-supervisor`
SHALL never depend on `api` or `app`.

#### Scenario: Adding a protocol never touches the supervisor
- **WHEN** a new `workers/worker-<proto>` module is added to serve a new
  protocol
- **THEN** no code in `runtime-supervisor` changes to support it

#### Scenario: Forbidden dependency fails the build
- **WHEN** code in `workers/worker-opcua` attempts to depend on `domain` or
  `persistence`
- **THEN** the Gradle module graph makes this fail to compile, and an
  ArchUnit test additionally asserts the layer rule inside modules where the
  module graph alone can't (e.g. `domain` must not import `api` packages)

### Requirement: No new base class or generic abstraction without approval
The protocol-neutral model and the `ProtocolDataSource` contract are the only
approved cross-cutting abstractions. A new shared base class, generic
framework, or similar abstraction SHALL NOT be introduced without explicit
owner approval first.

#### Scenario: A proposed generic abstraction is rejected without approval
- **WHEN** a change proposes a new shared base class for "future protocol
  extensibility" with no owner sign-off
- **THEN** the change is not accepted as-is; it needs explicit approval first

### Requirement: Workers are lean JVMs
Worker modules SHALL run without Spring, optimized for memory footprint and
sustained throughput rather than fast startup, and SHALL build as standalone
runnable distributions (`installDist`) that the supervisor spawns as external
processes.

#### Scenario: Worker module has no Spring dependency
- **WHEN** a worker module's dependencies are inspected
- **THEN** no `spring-*` dependency is present

