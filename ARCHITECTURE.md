# Architecture

Change rule: do not change this file without explicit user approval. Propose
changes first with rationale and expected impact.

Scope: the smallest set of binding constraints every developer-agent must
follow. Product capabilities live in `SPEC.md`; UI in `DESIGN.md`; glossary in
`MEMORY.md`. Detailed rationale and prior ADRs live in git history.

## Overview

Modular monolith on Java (LTS, 21/25) + Spring Boot, with a React/TypeScript Web
UI. The backend hosts the modules and a runtime supervisor; the runtime serves
multiple protocol families (OPC UA, Modbus TCP) behind one worker contract, and
each protocol data-source runs as an isolated out-of-process worker for fault
isolation.
Persistence: PostgreSQL (relational) + TimescaleDB (value timelines) + an
object-storage abstraction (artifacts). The core risk is a reliable protocol
simulator runtime — fidelity, fault isolation, determinism, and reproducible
evidence rank above CRUD convenience.

## Dependency policy

- No new dependency without explicit approval. Use only approved libraries.
- Approved stack: Spring Boot 3.x (REST/OpenAPI via springdoc, SSE/WebSocket);
  Eclipse Milo (OPC UA — certified server SDK, Apache 2.0); j2mod (Modbus TCP —
  server/slave, Apache 2.0); PostgreSQL + TimescaleDB; Flyway + PostgreSQL JDBC +
  jOOQ (typed SQL, no heavy ORM); Gradle (Kotlin DSL) build;
  React + TypeScript + Vite + React Router + TanStack Query/Table + Zustand +
  Radix UI + Tailwind; OAuth2/OIDC; Docker Compose; JUnit 5/Testcontainers/
  AssertJ/Vitest/Testing Library/Playwright.
- Do not introduce: Node.js/NestJS or Python as the primary simulator runtime
  (Node rejected for server-side scaling/memory risk; Python viable but lacks a
  certified OPC UA server — Java/Milo chosen for certified fidelity under Apache
  2.0); microservices; Kubernetes as the baseline deploy target; a second
  primary database; plaintext secrets anywhere.

## Architectural constraints

- Modular monolith. Dependencies flow toward shared/lower modules; protocol and
  runtime modules must not depend on UI-facing modules.
- A protocol-neutral internal schema and value model is the source of truth.
  Each protocol worker projects it onto its native address model (OPC UA
  nodes/namespaces ↔ Modbus coils/discrete-inputs/holding/input registers, with
  explicit data-type and word-order/endianness mapping). Recording, replay,
  synthetic generation, scenarios, and faults operate only on the neutral model,
  never per-protocol.
- A single `ProtocolDataSource` worker contract is implemented by both the
  OPC UA (Milo) and Modbus (j2mod) workers. The supervisor stays
  protocol-agnostic; protocol-specific code lives only inside the worker.
- Data-sources run out-of-process under the runtime supervisor. A worker crash
  must not take down the backend. Supervisor owns lifecycle, IPC, health, port
  allocation (static pre-mapped range), and resource governance.
- Workers are lean JVMs (Milo/j2mod + the IPC layer only, no Spring). GraalVM
  native-image workers are the approved path to cut startup/heap when many
  sources run concurrently and to ship a self-contained local build.
- Supervisor⇄worker IPC is local-only (loopback) and versioned; never exposed
  externally. Mismatched contract versions are refused, not tolerated.
- Faults are a product feature: distinguish intentional faults from real
  failures by intent tag. Never auto-"heal" an intentional fault; only
  unexpected failures trigger restart-with-backoff. Faults exist at both layers:
  protocol-neutral (bad/stale value, delay) and protocol-specific (OPC UA Bad
  status codes / Modbus exception codes 0x01–0x04, timeouts, connection drops),
  mapped per worker.
- APIs: REST + OpenAPI for commands/queries/test-control, path-based major
  versioning (`/api/v1`), additive within a major. SSE/WebSocket for live.
- Two separate data paths: the recording path captures every value change (no
  sampling); the live path is conflated/throttled (~4–10 Hz/node).
- Determinism is guaranteed for generated value content and scenario step
  ordering (explicit clock, seeded random) — not for client delivery timing.
- Persistence by data shape: PostgreSQL relational, TimescaleDB hypertables for
  timelines (batched writes + backpressure), object-storage abstraction for
  artifacts. No large blobs in PostgreSQL.
- Secrets and PKI material come from env vars / external secret store — never in
  repo files or exports. Exports exclude secrets/private keys by default.
- All exportable/importable artifacts and schemas are versioned with
  upgrade-on-import; newer-than-supported versions fail safely.

## Abstraction policy

- No new base class without approval.
- No new generic abstraction without written justification. The protocol-neutral
  model and the `ProtocolDataSource` worker contract are approved abstractions.
- Do not split into microservices before scale or ownership boundaries require
  it.
