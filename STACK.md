# Technology Stack

Approved technologies only. No new dependency without explicit approval (see
`ARCHITECTURE.md` → Governance). Rationale and rejected alternatives live in git
history.

## Backend
- Java LTS (21/25); Spring Boot 3.x — REST/OpenAPI via springdoc, SSE/WebSocket.
- Eclipse Milo — OPC UA server SDK (Apache 2.0).
- j2mod — Modbus TCP server/slave (Apache 2.0).
- PostgreSQL + TimescaleDB; Flyway migrations; PostgreSQL JDBC + jOOQ (typed SQL,
  no heavy ORM).
- Workers: lean JVMs — protocol SDK + IPC layer only, no Spring. Workers are
  long-lived, so optimize for per-process memory, sustained throughput, and small
  runtime surface rather than startup time. Default runtime is the plain JVM,
  optionally with AppCDS; self-contained local builds use jlink/jpackage. GraalVM
  native-image is optional per workload and must be validated for throughput before
  adoption, especially on the hot recording path.

## Frontend
- React + TypeScript + Vite + React Router + TanStack Query/Table + Zustand +
  Radix UI + Tailwind.

## Platform and tooling
- Auth: OAuth2/OIDC (e.g. Keycloak, AWS Cognito, Azure Entra ID).
- Build: Gradle (Kotlin DSL). Deploy: Docker Compose.
- Testing: JUnit 5, Testcontainers, AssertJ, Vitest, Testing Library, Playwright.

## Rejected (do not introduce)
- Node.js/NestJS as the primary simulator runtime — server-side scaling/memory risk.
- Python as the primary runtime — no certified OPC UA server (Java/Milo chosen for
  certified fidelity under Apache 2.0).
- Microservices; Kubernetes as the baseline deploy target; a second primary
  database; plaintext secrets anywhere.
